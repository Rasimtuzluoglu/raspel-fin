package com.raspel.cardtracker.domain.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void createUser_shouldCreateUserWithValidData() {
        String username = "testuser";
        String rawPassword = "Secure1Pass";
        String encodedPassword = "encoded_Secure1Pass";
        String fullName = "Test User";
        Role role = Role.USER;

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);

        AppUser savedUser = AppUser.builder()
                .id(1L)
                .username(username)
                .password(encodedPassword)
                .fullName(fullName)
                .role(role)
                .active(true)
                .build();
        when(userRepository.save(any(AppUser.class))).thenReturn(savedUser);

        AppUser result = userService.createUser(username, rawPassword, fullName, role);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo(username);
        assertThat(result.getFullName()).isEqualTo(fullName);
        assertThat(result.getRole()).isEqualTo(role);
        assertThat(result.getActive()).isTrue();

        verify(passwordEncoder).encode(rawPassword);
        verify(userRepository).save(any(AppUser.class));
    }

    @Test
    void createUser_shouldThrowWhenPasswordIsNull() {
        assertThatThrownBy(() -> userService.createUser("testuser", null, "Full Name", Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("en az");
    }

    @Test
    void createUser_shouldThrowWhenPasswordIsTooShort() {
        assertThatThrownBy(() -> userService.createUser("testuser", "Ab1", "Full Name", Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("en az");
    }

    @Test
    void createUser_shouldThrowWhenUsernameAlreadyExists() {
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser("existinguser", "Password1", "Full Name", Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bu kullanıcı adı zaten kullanılıyor");
    }

    @Test
    void createUser_shouldEncodePasswordBeforeSaving() {
        String rawPassword = "Mypassword1";
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn("encoded_Mypassword1");
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.createUser("newuser", rawPassword, "New User", Role.USER);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded_Mypassword1");
        assertThat(captor.getValue().getPassword()).isNotEqualTo(rawPassword);
    }

    @Test
    void loadUserByUsername_shouldReturnUserDetailsWhenUserExists() {
        String username = "testuser";
        AppUser appUser = AppUser.builder()
                .id(1L)
                .username(username)
                .password("hashedpassword")
                .role(Role.USER)
                .active(true)
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(appUser));

        UserDetails userDetails = userService.loadUserByUsername(username);

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo(username);
        assertThat(userDetails.getPassword()).isEqualTo("hashedpassword");
        assertThat(userDetails.getAuthorities()).hasSize(1);
        assertThat(userDetails.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isEnabled()).isTrue();
    }

    @Test
    void loadUserByUsername_shouldThrowWhenUserNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Geçersiz kullanıcı adı veya şifre");
    }

    @Test
    void loadUserByUsername_shouldThrowWhenUserIsInactive() {
        AppUser inactiveUser = AppUser.builder()
                .id(1L)
                .username("inactive")
                .password("hashed")
                .role(Role.USER)
                .active(false)
                .build();

        when(userRepository.findByUsername("inactive")).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> userService.loadUserByUsername("inactive"))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("Kullanıcı devre dışı");
    }
}
