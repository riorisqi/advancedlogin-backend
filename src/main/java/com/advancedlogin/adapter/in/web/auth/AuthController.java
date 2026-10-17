package com.advancedlogin.adapter.in.web.auth;

import com.advancedlogin.adapter.in.web.security.SessionUser;
import com.advancedlogin.adapter.in.web.security.WebSessionAuthenticator;
import com.advancedlogin.application.auth.port.in.AuthenticateUserUseCase;
import com.advancedlogin.application.auth.port.in.GetUserUseCase;
import com.advancedlogin.domain.user.User;
import com.advancedlogin.domain.user.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Password login. Logout is Spring Security's LogoutFilter at POST /api/auth/logout (see SecurityConfig). */
@RestController
class AuthController {

    private final AuthenticateUserUseCase authenticateUser;
    private final GetUserUseCase getUser;
    private final WebSessionAuthenticator sessionAuthenticator;

    AuthController(AuthenticateUserUseCase authenticateUser, GetUserUseCase getUser,
                   WebSessionAuthenticator sessionAuthenticator) {
        this.authenticateUser = authenticateUser;
        this.getUser = getUser;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    /** Touching the lazily created CsrfToken makes Spring write the XSRF-TOKEN cookie for the SPA. */
    @GetMapping("/api/auth/csrf")
    ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/login")
    UserResponse login(@Valid @RequestBody LoginRequest body,
                       HttpServletRequest request, HttpServletResponse response) {
        User user = authenticateUser.authenticate(body.username(), body.password());
        sessionAuthenticator.logIn(user, request, response);
        return UserResponse.from(user);
    }

    @GetMapping("/api/me")
    ResponseEntity<UserResponse> me(@AuthenticationPrincipal SessionUser principal) {
        // The session can outlive the user (e.g. deleted account): treat that as logged out.
        return getUser.getUser(new UserId(principal.userId()))
                .map(UserResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    record LoginRequest(@NotBlank @Size(max = 100) String username,
                        @NotBlank @Size(max = 200) String password) {
    }

    record UserResponse(String id, String username, String displayName) {

        static UserResponse from(User user) {
            return new UserResponse(user.id().value(), user.username(), user.displayName());
        }
    }
}
