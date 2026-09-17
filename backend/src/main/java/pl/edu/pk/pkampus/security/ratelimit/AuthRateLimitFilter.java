package pl.edu.pk.pkampus.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.edu.pk.pkampus.common.ApiResponse;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final AuthRateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Value("${app.security.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        AuthRateLimitEndpoint endpoint = AuthRateLimitEndpoint.fromRequest(
                request.getMethod(),
                request.getRequestURI()
        );

        if (endpoint != null) {
            String clientIp = extractClientIp(request);
            ConsumptionProbe probe = rateLimiterService.tryConsume(endpoint, clientIp);

            if (!probe.isConsumed()) {
                long secondsToWait = (probe.getNanosToWaitForRefill() / 1_000_000_000L) + 1;
                log.warn("Rate limit exceeded for {} from IP: {}. Retry after {}s",
                        endpoint.name(), clientIp, secondsToWait);

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader("Retry-After", String.valueOf(secondsToWait));
                response.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(secondsToWait));
                response.setHeader("X-Rate-Limit-Remaining", "0");

                String message = switch (endpoint) {
                    case LOGIN -> "Too many login attempts. Please try again in " + secondsToWait + " seconds.";
                    case REGISTER -> "Too many registration attempts. Please try again in " + secondsToWait + " seconds.";
                    case REFRESH -> "Too many token refresh attempts. Please try again in " + secondsToWait + " seconds.";
                };

                ApiResponse<Void> apiResponse = ApiResponse.error(message);
                response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
                return;
            }

            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
        }

        filterChain.doFilter(request, response);
    }

    public String extractClientIp(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }
        }
        return request.getRemoteAddr();
    }

    void setTrustForwardedHeaders(boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }
}
