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

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/actuator/health")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/actuator/**")).hasRole("ADMIN")
                .requestMatchers(new AntPathRequestMatcher("/swagger-ui/**")).hasRole("ADMIN")
                .requestMatchers(new AntPathRequestMatcher("/swagger-ui.html")).hasRole("ADMIN")
                .requestMatchers(new AntPathRequestMatcher("/v3/api-docs/**")).hasRole("ADMIN")
                .requestMatchers(new AntPathRequestMatcher("/api-docs/**")).hasRole("ADMIN")
                .requestMatchers(new AntPathRequestMatcher("/api/**")).authenticated()
        );

        http.sessionManagement(session -> session.maximumSessions(1).expiredUrl("/login?expired"));

        http.headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
        );

        http.csrf(csrf -> csrf.ignoringRequestMatchers(new AntPathRequestMatcher("/api/**")));

        super.configure(http);
        setLoginView(http, LoginView.class);
    }
}
