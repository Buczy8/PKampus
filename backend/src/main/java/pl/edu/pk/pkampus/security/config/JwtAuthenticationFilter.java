package pl.edu.pk.pkampus.security.config;

import io.jsonwebtoken.JwtException;
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
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.AuthenticatedUserCache;
import pl.edu.pk.pkampus.security.jwt.JwtService;
import pl.edu.pk.pkampus.security.jwt.TokenRevocationService;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TokenRevocationService tokenRevocationService;
    private final AuthenticatedUserCache authenticatedUserCache;

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
            // Single signature verification per request: parse once and reuse
            // the claims for expiry, identity, revocation and validity checks.
            JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(token);

            if (claims.expiresAt().isBefore(Instant.now())) {
                filterChain.doFilter(request, response);
                return;
            }

            final UUID userId = claims.userId();

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                if (tokenRevocationService.isRevoked(userId)) {
                    log.warn("Blocked request for revoked user token: {}", userId);
                    filterChain.doFilter(request, response);
                    return;
                }

                User user = authenticatedUserCache.getOrLoad(userId, id ->
                        userRepository.findById(id).orElse(null)
                );

                if (user != null
                        && isAuthenticationEligible(request, user.getStatus())
                        && user.getUsername() != null
                        && user.getUsername().equals(claims.email())) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            user.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authenticated user {} with authorities {}", user.getEmail(), user.getAuthorities());
                } else if (user == null) {
                    authenticatedUserCache.invalidate(userId);
                }
            }
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Failed to authenticate JWT token", e);
        } catch (Exception e) {
            log.warn("Failed to authenticate JWT token", e);
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isAuthenticationEligible(HttpServletRequest request, UserStatus status) {
        if (isLoginEligible(status)) {
            return true;
        }
        // FR-CARD-03: allow card endpoint to authenticate BLOCKED/CHECKED_OUT so API returns 403 board.
        return isResidentCardRequest(request) && isCardStatusBoard(status);
    }

    private static boolean isLoginEligible(UserStatus status) {
        return status == UserStatus.ACTIVE || status == UserStatus.MUST_CHANGE_PASSWORD;
    }

    private static boolean isCardStatusBoard(UserStatus status) {
        return status == UserStatus.BLOCKED || status == UserStatus.CHECKED_OUT;
    }

    private static boolean isResidentCardRequest(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        // exact match only: suffix matching in an authentication gate invites
        // bypasses whenever firewall or routing rules change (defense in depth)
        return "/api/v1/profile/card".equals(request.getRequestURI());
    }
}
