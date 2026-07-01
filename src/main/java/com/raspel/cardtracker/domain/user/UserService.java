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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Şifre politikası:
     * - En az 8 karakter
     * - En az 1 büyük harf
     * - En az 1 rakam
     */
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("\\p{Lu}");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Geçersiz kullanıcı adı veya şifre"));

        if (!appUser.getActive()) {
            throw new DisabledException("Kullanıcı devre dışı: " + username);
        }

        if (isAccountLocked(appUser)) {
            throw new LockedException("Hesabınız 15 dakika süreyle kilitlenmiştir. Lütfen daha sonra tekrar deneyin.");
        }

        return new User(
                appUser.getUsername(),
                appUser.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + appUser.getRole().name()))
        );
    }

    @Transactional(readOnly = true)
    public List<AppUser> findAll() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<AppUser> findAllActive() {
        return userRepository.findAllByActiveTrue();
    }

    @Transactional(readOnly = true)
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
        return !LocalDateTime.now().isAfter(user.getLockedUntil());
    }

    public AppUser createUser(String username, String rawPassword, String fullName, Role role) {
        validatePassword(rawPassword);
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Bu kullanıcı adı zaten kullanılıyor: " + username);
        }

        AppUser user = AppUser.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .fullName(fullName)
                .role(role)
                .active(true)
                .build();

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public boolean validatePassword(String username, String plainPassword) {
        return userRepository.findByUsername(username)
                .map(user -> passwordEncoder.matches(plainPassword, user.getPassword()))
                .orElse(false);
    }

    /**
     * Şifrenin güvenlik politikasına uygunluğunu doğrular.
     * Geçersizse {@link IllegalArgumentException} fırlatır.
     */
    public void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Şifre en az " + MIN_PASSWORD_LENGTH + " karakter olmalıdır");
        }
        if (!UPPERCASE_PATTERN.matcher(password).find()) {
            throw new IllegalArgumentException("Şifre en az 1 büyük harf içermelidir");
        }
        if (!DIGIT_PATTERN.matcher(password).find()) {
            throw new IllegalArgumentException("Şifre en az 1 rakam içermelidir");
        }
    }

    public void updatePassword(Long userId, String newPassword) {
        validatePassword(newPassword);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı: " + userId));
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    public void markPasswordChanged(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setMustChangePassword(false);
            userRepository.save(user);
        });
    }

    @Transactional(readOnly = true)
    public boolean mustChangePassword(String username) {
        return userRepository.findByUsername(username)
                .map(AppUser::getMustChangePassword)
                .orElse(false);
    }

    public void deactivateUser(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı: " + userId));
        if (Role.ADMIN.equals(user.getRole())) {
            throw new IllegalStateException("Yönetici (ADMIN) rolündeki kullanıcılar devre dışı bırakılamaz!");
        }
        user.setActive(false);
        userRepository.save(user);
    }

    public void activateUser(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı: " + userId));
        user.setActive(true);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
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

    public String generateTelegramVerificationCode(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı"));
        String code = String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
        user.setTelegramVerificationCode(code);
        userRepository.save(user);
        return code;
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findByTelegramChatId(Long chatId) {
        return userRepository.findByTelegramChatId(chatId);
    }

    public void disconnectTelegram(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setTelegramChatId(null);
            user.setTelegramVerificationCode(null);
            userRepository.save(user);
        });
    }

    public boolean linkTelegramChatId(String verificationCode, Long chatId) {
        AppUser user = userRepository.findByTelegramVerificationCode(verificationCode)
                .orElse(null);
        if (user == null) return false;

        Optional<AppUser> existingChatUser = userRepository.findByTelegramChatId(chatId);
        if (existingChatUser.isPresent() && !existingChatUser.get().getId().equals(user.getId())) {
            return false;
        }

        userRepository.linkTelegramChatId(user.getId(), chatId);
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findByTelegramVerificationCode(String code) {
        return userRepository.findByTelegramVerificationCode(code);
    }

    public boolean disconnectTelegramByChatId(Long chatId) {
        Optional<AppUser> userOpt = userRepository.findByTelegramChatId(chatId);
        if (userOpt.isEmpty()) return false;
        AppUser user = userOpt.get();
        user.setTelegramChatId(null);
        user.setTelegramVerificationCode(null);
        userRepository.save(user);
        return true;
    }

    public void deleteUser(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (Role.ADMIN.equals(user.getRole())) {
                throw new IllegalArgumentException("Yönetici (ADMIN) rolündeki kullanıcılar silinemez!");
            }
            userRepository.delete(user);
        });
    }
}
