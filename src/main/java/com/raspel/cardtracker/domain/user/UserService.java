package com.raspel.cardtracker.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı: " + username));

        if (!appUser.getActive()) {
            throw new DisabledException("Kullanıcı devre dışı: " + username);
        }

        if (isAccountLocked(appUser)) {
            throw new LockedException("Hesabınız 15 dakika süreyle kilitlenmiştir. Lütfen daha sonra tekrar deneyin.");
        }

        return new User(
                appUser.getUsername(),
                appUser.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + appUser.getRole()))
        );
    }

    public List<AppUser> findAll() {
        return userRepository.findAll();
    }

    public List<AppUser> findAllActive() {
        return userRepository.findAllByActiveTrue();
    }

    public Optional<AppUser> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public AppUser save(AppUser user) {
        return userRepository.save(user);
    }

    public void updateLastLogin(String username) {
        userRepository.updateLastLogin(username, LocalDateTime.now());
    }

    public void recordFailedLogin(String username) {
        userRepository.recordFailedLogin(username, LocalDateTime.now().plusMinutes(15));
    }

    public void recordSuccessfulLogin(String username) {
        userRepository.recordSuccessfulLogin(username);
    }

    private boolean isAccountLocked(AppUser user) {
        if (user.getLockedUntil() == null) {
            return false;
        }
        if (LocalDateTime.now().isAfter(user.getLockedUntil())) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
            return false;
        }
        return true;
    }

    public AppUser createUser(String username, String password, String fullName, String role) {
        if (password == null || password.length() < 4) throw new IllegalArgumentException("Şifre en az 4 karakter olmalıdır");
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Bu kullanıcı adı zaten kullanılıyor: " + username);
        }

        AppUser user = AppUser.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .fullName(fullName)
                .role(role)
                .active(true)
                .build();

        return userRepository.save(user);
    }

    public boolean validatePassword(String username, String plainPassword) {
        return userRepository.findByUsername(username)
                .map(user -> passwordEncoder.matches(plainPassword, user.getPassword()))
                .orElse(false);
    }

    public void updatePassword(Long userId, String newPassword) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
        });
    }

    public void deactivateUser(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if ("ADMIN".equalsIgnoreCase(user.getRole())) {
                throw new IllegalStateException("Yönetici (ADMIN) rolündeki kullanıcılar devre dışı bırakılamaz!");
            }
            user.setActive(false);
            userRepository.save(user);
        });
    }

    public void activateUser(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setActive(true);
            userRepository.save(user);
        });
    }

    public DashboardConfig getDashboardConfig(String username) {
        return userRepository.findByUsername(username)
                .map(user -> DashboardConfig.fromJson(user.getDashboardConfig()))
                .orElse(DashboardConfig.createDefault());
    }

    public void saveDashboardConfig(String username, DashboardConfig config) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setDashboardConfig(config.toJson());
            userRepository.save(user);
        });
    }
}
