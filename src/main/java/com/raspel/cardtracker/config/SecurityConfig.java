package com.raspel.cardtracker.config;

import com.vaadin.flow.spring.security.VaadinWebSecurity;
import com.raspel.cardtracker.domain.user.Role;
import com.raspel.cardtracker.ui.LoginView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends VaadinWebSecurity {

    // CSRF protection is handled automatically by VaadinWebSecurity.
    // Vaadin uses a double-submit cookie pattern; no explicit CSRF configuration needed.

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/images/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/line-awesome/**")).permitAll()
        );

        http.sessionManagement(session -> session.maximumSessions(1).expiredUrl("/login?expired"));

        super.configure(http);
        setLoginView(http, LoginView.class);
    }
}
