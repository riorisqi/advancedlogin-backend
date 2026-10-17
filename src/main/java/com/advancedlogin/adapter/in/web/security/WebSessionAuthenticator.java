package com.advancedlogin.adapter.in.web.security;

import com.advancedlogin.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Turns "this user is verified" into "this browser is logged in". Used by password login now and
 * by the QR code exchange later, so both get identical session handling.
 *
 * <p>Spring's form login does these steps for us; with a JSON endpoint we do them explicitly:
 * <ol>
 *   <li>session strategy: new session id (prevents session fixation) + fresh CSRF token</li>
 *   <li>put the Authentication in the SecurityContext</li>
 *   <li>save the context into the HTTP session, which Spring Session stores in Redis</li>
 * </ol>
 */
@Component
public class WebSessionAuthenticator {

    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    WebSessionAuthenticator(SecurityContextRepository securityContextRepository,
                            SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    public void logIn(User user, HttpServletRequest request, HttpServletResponse response) {
        SessionUser principal = new SessionUser(user.id().value(), user.username());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, AuthorityUtils.createAuthorityList("ROLE_USER"));

        request.getSession(true);
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
