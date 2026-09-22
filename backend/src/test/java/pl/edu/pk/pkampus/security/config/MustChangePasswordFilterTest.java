package pl.edu.pk.pkampus.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MustChangePasswordFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MustChangePasswordFilter filter;

    @Mock
    private FilterChain filterChain;

    private User mustChangePasswordUser;
    private User activeUser;

    @BeforeEach
    void setUp() {
        filter = new MustChangePasswordFilter(objectMapper);
        SecurityContextHolder.clearContext();

        mustChangePasswordUser = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .status(UserStatus.MUST_CHANGE_PASSWORD)
                .build();

        activeUser = User.builder()
                .id(UUID.randomUUID())
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
    void shouldContinueFilterChainWhenNoAuthentication() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/issues");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldContinueFilterChainWhenUserIsActive() throws Exception {
        // Arrange
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(activeUser, null, activeUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/issues");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowMustChangePasswordUserToAccessGetAuthMe() throws Exception {
        // Arrange
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(mustChangePasswordUser, null, mustChangePasswordUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowMustChangePasswordUserToAccessChangePassword() throws Exception {
        // Arrange
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(mustChangePasswordUser, null, mustChangePasswordUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/change-password");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowMustChangePasswordUserToAccessRefreshToken() throws Exception {
        // Arrange
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(mustChangePasswordUser, null, mustChangePasswordUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowMustChangePasswordUserToAccessLogout() throws Exception {
        // Arrange
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(mustChangePasswordUser, null, mustChangePasswordUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldBlockMustChangePasswordUserFromAccessingOtherGetEndpoints() throws Exception {
        // Arrange
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(mustChangePasswordUser, null, mustChangePasswordUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/issues");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, never()).doFilter(request, response);
        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());

        ApiResponse<?> apiResponse = objectMapper.readValue(response.getContentAsString(), ApiResponse.class);
        assertFalse(apiResponse.success());
        assertEquals("Password change required before accessing this resource", apiResponse.message());
    }

    @Test
    void shouldBlockMustChangePasswordUserFromAccessingOtherPostEndpoints() throws Exception {
        // Arrange
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(mustChangePasswordUser, null, mustChangePasswordUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/laundry/reservations");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, never()).doFilter(request, response);
        assertEquals(403, response.getStatus());
    }
}
