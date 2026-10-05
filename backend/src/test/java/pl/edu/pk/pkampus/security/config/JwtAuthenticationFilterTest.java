package pl.edu.pk.pkampus.security.config;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.AuthenticatedUserCache;
import pl.edu.pk.pkampus.security.jwt.JwtService;
import pl.edu.pk.pkampus.security.jwt.TokenRevocationService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenRevocationService tokenRevocationService;

    @Mock
    private AuthenticatedUserCache authenticatedUserCache;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private User testUser;
    private final UUID testUserId = UUID.randomUUID();
    private JwtService.AccessTokenClaims validClaims;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        testUser = User.builder()
                .id(testUserId)
                .email("student@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .build();

        validClaims = new JwtService.AccessTokenClaims(
                testUserId, "student@pk.edu.pl", Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldContinueFilterChainWithoutAuthenticationWhenNoHeader() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueFilterChainWithoutAuthenticationWhenHeaderDoesNotStartWithBearer() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic user:pass");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAuthenticateActiveUserWithValidToken() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.parseAccessToken("valid-jwt-token")).thenReturn(validClaims);
        when(tokenRevocationService.isRevoked(testUserId)).thenReturn(false);
        when(authenticatedUserCache.getOrLoad(eq(testUserId), any())).thenReturn(testUser);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("student@pk.edu.pl", SecurityContextHolder.getContext().getAuthentication().getName());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a -> a.getAuthority().equals("ROLE_RESIDENT")));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAuthenticateMustChangePasswordUserWithValidToken() throws Exception {
        // Arrange
        testUser.setStatus(UserStatus.MUST_CHANGE_PASSWORD);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.parseAccessToken("valid-jwt-token")).thenReturn(validClaims);
        when(tokenRevocationService.isRevoked(testUserId)).thenReturn(false);
        when(authenticatedUserCache.getOrLoad(eq(testUserId), any())).thenReturn(testUser);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueFilterChainWhenTokenIsExpired() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer expired-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        JwtService.AccessTokenClaims expiredClaims = new JwtService.AccessTokenClaims(
                testUserId, "student@pk.edu.pl", Instant.now().minus(1, ChronoUnit.MINUTES));
        when(jwtService.parseAccessToken("expired-jwt-token")).thenReturn(expiredClaims);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(authenticatedUserCache, never()).getOrLoad(any(), any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRejectWhenUserIsRevokedInBlacklist() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer revoked-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.parseAccessToken("revoked-jwt-token")).thenReturn(validClaims);
        when(tokenRevocationService.isRevoked(testUserId)).thenReturn(true);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(authenticatedUserCache, never()).getOrLoad(any(), any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRejectWhenUserStatusIsNotActive() throws Exception {
        // Arrange
        testUser.setStatus(UserStatus.BLOCKED);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/issues");
        request.addHeader("Authorization", "Bearer blocked-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.parseAccessToken("blocked-jwt-token")).thenReturn(validClaims);
        when(tokenRevocationService.isRevoked(testUserId)).thenReturn(false);
        when(authenticatedUserCache.getOrLoad(eq(testUserId), any())).thenReturn(testUser);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRejectWhenTokenEmailDoesNotMatchUser() throws Exception {
        // Arrange (stale token after an email change must not authenticate)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer mismatched-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        JwtService.AccessTokenClaims mismatchedClaims = new JwtService.AccessTokenClaims(
                testUserId, "other@pk.edu.pl", Instant.now().plus(15, ChronoUnit.MINUTES));
        when(jwtService.parseAccessToken("mismatched-jwt-token")).thenReturn(mismatchedClaims);
        when(tokenRevocationService.isRevoked(testUserId)).thenReturn(false);
        when(authenticatedUserCache.getOrLoad(eq(testUserId), any())).thenReturn(testUser);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAuthenticateBlockedUserWhenRequestingResidentCardEndpoint() throws Exception {
        // Arrange (FR-CARD-03: allow card endpoint to authenticate BLOCKED so API returns 403 board)
        testUser.setStatus(UserStatus.BLOCKED);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/profile/card");
        request.addHeader("Authorization", "Bearer valid-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.parseAccessToken("valid-jwt-token")).thenReturn(validClaims);
        when(tokenRevocationService.isRevoked(testUserId)).thenReturn(false);
        when(authenticatedUserCache.getOrLoad(eq(testUserId), any())).thenReturn(testUser);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAuthenticateCheckedOutUserWhenRequestingResidentCardEndpoint() throws Exception {
        // Arrange (FR-CARD-03: allow card endpoint to authenticate CHECKED_OUT)
        testUser.setStatus(UserStatus.CHECKED_OUT);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/profile/card");
        request.addHeader("Authorization", "Bearer valid-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.parseAccessToken("valid-jwt-token")).thenReturn(validClaims);
        when(tokenRevocationService.isRevoked(testUserId)).thenReturn(false);
        when(authenticatedUserCache.getOrLoad(eq(testUserId), any())).thenReturn(testUser);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticateBlockedUserForSuffixSpoofedPath() throws Exception {
        // Arrange (defense in depth: only the exact card path may authenticate blocked users)
        testUser.setStatus(UserStatus.BLOCKED);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/issues/api/v1/profile/card");
        request.addHeader("Authorization", "Bearer valid-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.parseAccessToken("valid-jwt-token")).thenReturn(validClaims);
        when(tokenRevocationService.isRevoked(testUserId)).thenReturn(false);
        when(authenticatedUserCache.getOrLoad(eq(testUserId), any())).thenReturn(testUser);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldInvalidateCacheAndNotAuthenticateWhenUserIsNull() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.parseAccessToken("valid-jwt-token")).thenReturn(validClaims);
        when(tokenRevocationService.isRevoked(testUserId)).thenReturn(false);
        when(authenticatedUserCache.getOrLoad(eq(testUserId), any())).thenReturn(null);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(authenticatedUserCache).invalidate(testUserId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueFilterChainWhenExceptionThrownDuringTokenParsing() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer malformed-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.parseAccessToken("malformed-token")).thenThrow(new JwtException("Malformed JWT"));

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
