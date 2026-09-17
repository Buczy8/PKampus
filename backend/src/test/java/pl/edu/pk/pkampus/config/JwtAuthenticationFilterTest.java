package pl.edu.pk.pkampus.config;

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
import pl.edu.pk.pkampus.model.User;
import pl.edu.pk.pkampus.model.UserRole;
import pl.edu.pk.pkampus.model.UserStatus;
import pl.edu.pk.pkampus.repository.UserRepository;
import pl.edu.pk.pkampus.service.JwtService;
import pl.edu.pk.pkampus.service.TokenRevocationService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private User testUser;
    private final UUID testUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        testUser = User.builder()
                .id(testUserId)
                .email("student@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldContinueFilterChainWithoutAuthenticationWhenNoHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAuthenticateActiveUserWithValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.extractUserId("valid-jwt-token")).thenReturn(testUserId);
        when(jwtService.extractEmail("valid-jwt-token")).thenReturn("student@pk.edu.pl");
        when(tokenRevocationService.isRevoked(testUserId)).thenReturn(false);
        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));
        when(jwtService.isTokenValid("valid-jwt-token", testUser)).thenReturn(true);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("student@pk.edu.pl", SecurityContextHolder.getContext().getAuthentication().getName());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a -> a.getAuthority().equals("ROLE_RESIDENT")));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRejectWhenUserIsRevokedInBlacklist() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer revoked-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.extractUserId("revoked-jwt-token")).thenReturn(testUserId);
        when(jwtService.extractEmail("revoked-jwt-token")).thenReturn("student@pk.edu.pl");
        when(tokenRevocationService.isRevoked(testUserId)).thenReturn(true);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userRepository, never()).findByEmail(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRejectWhenUserStatusIsNotActive() throws Exception {
        testUser.setStatus(UserStatus.BLOCKED);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer blocked-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.extractUserId("blocked-jwt-token")).thenReturn(testUserId);
        when(jwtService.extractEmail("blocked-jwt-token")).thenReturn("student@pk.edu.pl");
        when(tokenRevocationService.isRevoked(testUserId)).thenReturn(false);
        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(testUser));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
