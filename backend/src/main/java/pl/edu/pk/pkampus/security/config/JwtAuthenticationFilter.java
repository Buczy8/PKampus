package pl.edu.pk.pkampus.security.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.security.jwt.JwtService;
import pl.edu.pk.pkampus.security.jwt.TokenRevocationService;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TokenRevocationService tokenRevocationService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        try {
            final UUID userId = jwtService.extractUserId(token);
            final String email = jwtService.extractEmail(token);

            // Verify if user is not already authenticated in current context
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Check Caffeine blacklist / revocation cache (UC-AUTH-02, NFR-SEC-01)
                if (tokenRevocationService.isRevoked(userId)) {
                    log.warn("Blocked request for revoked user token: {}", userId);
                    filterChain.doFilter(request, response);
                    return;
                }

                User user = userRepository.findByEmail(email).orElse(null);

                // User must be ACTIVE (BLOCKED, PENDING_EMAIL, PENDING_APPROVAL rejected)
                if (user != null && user.getStatus() == UserStatus.ACTIVE && jwtService.isTokenValid(token, user)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            user.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authenticated user {} with authorities {}", email, user.getAuthorities());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to authenticate JWT token: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
