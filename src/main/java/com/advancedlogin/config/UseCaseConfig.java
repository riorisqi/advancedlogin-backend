package com.advancedlogin.config;

import com.advancedlogin.application.auth.AuthenticateUserService;
import com.advancedlogin.application.auth.GetUserService;
import com.advancedlogin.application.auth.port.in.AuthenticateUserUseCase;
import com.advancedlogin.application.auth.port.in.GetUserUseCase;
import com.advancedlogin.application.auth.port.out.PasswordHasher;
import com.advancedlogin.application.auth.port.out.UserRepository;
import com.advancedlogin.application.health.CheckHealthService;
import com.advancedlogin.application.health.port.in.CheckHealthUseCase;
import com.advancedlogin.application.health.port.out.DataStoreHealthPort;
import com.advancedlogin.application.qr.CreateQrSessionService;
import com.advancedlogin.application.qr.QrSessionService;
import com.advancedlogin.application.qr.port.in.CreateQrSessionUseCase;
import com.advancedlogin.application.qr.port.out.QrSessionStore;
import com.advancedlogin.application.qr.port.out.QrStatusPublisher;
import com.advancedlogin.application.qr.port.out.RandomTokenGenerator;
import com.advancedlogin.domain.qr.QrTimeouts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Composition root for use cases: the only place that knows both the application services
 * and Spring. Adapters are picked up by component scanning; use cases are registered here.
 */
@Configuration
class UseCaseConfig {

    @Bean
    CheckHealthUseCase checkHealthUseCase(DataStoreHealthPort dataStoreHealthPort) {
        return new CheckHealthService(dataStoreHealthPort);
    }

    @Bean
    AuthenticateUserUseCase authenticateUserUseCase(UserRepository users, PasswordHasher passwordHasher) {
        return new AuthenticateUserService(users, passwordHasher);
    }

    @Bean
    GetUserUseCase getUserUseCase(UserRepository users) {
        return new GetUserService(users);
    }

    @Bean
    QrTimeouts qrTimeouts(@Value("${app.qr.timeouts.pending}") Duration pending,
                          @Value("${app.qr.timeouts.scanned}") Duration scanned,
                          @Value("${app.qr.timeouts.confirmed}") Duration confirmed,
                          @Value("${app.qr.timeouts.terminal}") Duration terminal) {
        return new QrTimeouts(pending, scanned, confirmed, terminal);
    }

    @Bean
    CreateQrSessionUseCase createQrSessionUseCase(QrSessionStore store, RandomTokenGenerator tokens, Clock clock,
                                                  QrTimeouts timeouts) {
        return new CreateQrSessionService(store, tokens, clock, timeouts);
    }

    /**
     * One object implements the status/scan/approve/reject/exchange use cases; Spring injects it
     * wherever any of those interfaces is asked for.
     */
    @Bean
    QrSessionService qrSessionService(QrSessionStore store, QrStatusPublisher publisher, RandomTokenGenerator tokens,
                                      UserRepository users, Clock clock, QrTimeouts timeouts) {
        return new QrSessionService(store, publisher, tokens, users, clock, timeouts);
    }

    /** Use cases ask a Clock for "now" instead of calling Instant.now(), so tests can fix time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
