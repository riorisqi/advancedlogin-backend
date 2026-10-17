package com.advancedlogin.adapter.in.web.ratelimit;

import com.advancedlogin.application.ratelimit.RateLimitDecision;
import com.advancedlogin.application.ratelimit.port.in.CheckRateLimitUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Per-IP limits, registered before Spring Security. The IP has already been through Tomcat's
 * RemoteIpValve, so a client can't fake it with its own X-Forwarded-For.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String method, Pattern path, String name) {
    }

    private static final String ID = "[A-Za-z0-9_-]+";
    private static final List<Rule> RULES = List.of(
            new Rule("POST", Pattern.compile("/api/qr/sessions"), "qr-create"),
            new Rule("GET", Pattern.compile("/api/qr/sessions/" + ID + "(/events)?"), "qr-status"),
            new Rule("POST", Pattern.compile("/api/qr/sessions/" + ID + "/exchange"), "qr-exchange"),
            new Rule("POST", Pattern.compile("/api/auth/login"), "login-ip"));

    private final CheckRateLimitUseCase rateLimits;

    public RateLimitFilter(CheckRateLimitUseCase rateLimits) {
        this.rateLimits = rateLimits;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Rule> rule = RULES.stream()
                .filter(r -> r.method().equals(request.getMethod()) && r.path().matcher(request.getRequestURI()).matches())
                .findFirst();
        if (rule.isPresent()) {
            RateLimitDecision decision = rateLimits.check(rule.get().name(), request.getRemoteAddr());
            if (!decision.allowed()) {
                writeTooManyRequests(response, decision);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** Same problem+json shape as ApiExceptionHandler, written by hand: filters run outside Spring MVC. */
    static void writeTooManyRequests(HttpServletResponse response, RateLimitDecision decision) throws IOException {
        long seconds = Math.max(1, (long) Math.ceil(decision.retryAfter().toMillis() / 1000.0));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Too many requests","status":429,\
                "detail":"Too many requests. Try again in %d seconds."}""".formatted(seconds));
    }
}
