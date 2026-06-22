package com.raspel.cardtracker.config;

import com.raspel.cardtracker.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationEventListener {

    private final UserService userService;

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();
        if (username != null && !username.isBlank()) {
            log.warn("Başarısız giriş denemesi: {}", username);
            try {
                userService.recordFailedLogin(username);
            } catch (Exception e) {
                log.error("Başarısız giriş kaydı yapılamadı: {}", username, e);
            }
        }
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        if (username != null && !username.isBlank()) {
            log.info("Başarılı giriş: {}", username);
            try {
                userService.recordSuccessfulLogin(username);
            } catch (Exception e) {
                log.error("Başarılı giriş kaydı yapılamadı: {}", username, e);
            }
        }
    }
}
