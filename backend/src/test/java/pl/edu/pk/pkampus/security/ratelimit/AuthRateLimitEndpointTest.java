package pl.edu.pk.pkampus.security.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimitEndpointTest {

    @Test
    void shouldResolveLoginEndpointFromPostRequest() {
        // Arrange & Act
        AuthRateLimitEndpoint endpoint = AuthRateLimitEndpoint.fromRequest("POST", "/api/v1/auth/login");

        // Assert
        assertEquals(AuthRateLimitEndpoint.LOGIN, endpoint);
        assertEquals("/api/v1/auth/login", endpoint.getPath());
    }

    @Test
    void shouldResolveRegisterEndpointFromPostRequest() {
        // Arrange & Act
        AuthRateLimitEndpoint endpoint = AuthRateLimitEndpoint.fromRequest("POST", "/api/v1/auth/register");

        // Assert
        assertEquals(AuthRateLimitEndpoint.REGISTER, endpoint);
        assertEquals("/api/v1/auth/register", endpoint.getPath());
    }

    @Test
    void shouldResolveRefreshEndpointFromPostRequest() {
        // Arrange & Act
        AuthRateLimitEndpoint endpoint = AuthRateLimitEndpoint.fromRequest("POST", "/api/v1/auth/refresh");

        // Assert
        assertEquals(AuthRateLimitEndpoint.REFRESH, endpoint);
        assertEquals("/api/v1/auth/refresh", endpoint.getPath());
    }

    @Test
    void shouldReturnNullForNonPostMethods() {
        // Arrange & Act & Assert
        assertNull(AuthRateLimitEndpoint.fromRequest("GET", "/api/v1/auth/login"));
        assertNull(AuthRateLimitEndpoint.fromRequest("PUT", "/api/v1/auth/register"));
        assertNull(AuthRateLimitEndpoint.fromRequest("DELETE", "/api/v1/auth/refresh"));
    }

    @Test
    void shouldReturnNullForUnknownOrNullPaths() {
        // Arrange & Act & Assert
        assertNull(AuthRateLimitEndpoint.fromRequest("POST", "/api/v1/auth/unknown"));
        assertNull(AuthRateLimitEndpoint.fromRequest("POST", null));
        assertNull(AuthRateLimitEndpoint.fromRequest("POST", ""));
    }
}
