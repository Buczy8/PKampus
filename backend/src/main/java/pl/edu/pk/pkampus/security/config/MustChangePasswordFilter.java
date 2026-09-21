package pl.edu.pk.pkampus.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.io.IOException;

/**
 * Restricts users in {@link UserStatus#MUST_CHANGE_PASSWORD} to auth bootstrap endpoints only.
 */
@Component
@RequiredArgsConstructor
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof User user
                && user.getStatus() == UserStatus.MUST_CHANGE_PASSWORD
                && !isAllowed(request.getMethod(), request.getRequestURI())) {

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getOutputStream(),
                    ApiResponse.error("Password change required before accessing this resource")
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isAllowed(String method, String path) {
        if ("GET".equalsIgnoreCase(method) && "/api/v1/auth/me".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method)) {
            return "/api/v1/auth/change-password".equals(path)
                    || "/api/v1/auth/refresh".equals(path)
                    || "/api/v1/auth/logout".equals(path);
        }
        return false;
    }
}
