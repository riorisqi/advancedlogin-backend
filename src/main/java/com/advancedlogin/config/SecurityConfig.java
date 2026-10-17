package com.advancedlogin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.util.List;

@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            SecurityContextRepository securityContextRepository,
                                            CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/auth/csrf", "/api/auth/login", "/error").permitAll()
                        // The desktop asking for a QR is, by definition, not logged in yet.
                        .requestMatchers(HttpMethod.POST, "/api/qr/sessions", "/api/qr/sessions/*/exchange").permitAll()
                        // Desktop status (snapshot + SSE stream): guarded by the qr_bind cookie instead.
                        .requestMatchers(HttpMethod.GET, "/api/qr/sessions/*", "/api/qr/sessions/*/events").permitAll()
                        .anyRequest().authenticated())

                // Session cookie auth => CSRF protection is mandatory. spa(): token in a JS-readable
                // XSRF-TOKEN cookie, sent back by the SPA as the X-XSRF-TOKEN header.
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))

                // Load/save the SecurityContext from the HTTP session (stored in Redis by Spring Session).
                .securityContext(context -> context.securityContextRepository(securityContextRepository))

                // An API answers 401, it doesn't redirect to an HTML login page.
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                // POST /api/auth/logout (CSRF-protected): invalidates the session, clears the
                // XSRF-TOKEN cookie, answers 204.
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));

        // No formLogin()/httpBasic(): login is our own JSON endpoint (AuthController).
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    /** What happens to the session at the moment of login. See WebSessionAuthenticator. */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),          // defeat session fixation
                new CsrfAuthenticationStrategy(csrfTokenRepository))); // rotate CSRF token
    }
}
