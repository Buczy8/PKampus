package pl.edu.pk.pkampus.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitFilterTest {

    @Mock
    private AuthRateLimiterService rateLimiterService;

    @Mock
    private FilterChain filterChain;

    private AuthRateLimitFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        filter = new AuthRateLimitFilter(rateLimiterService, objectMapper);
        filter.setTrustForwardedHeaders(false);
    }

    @Test
    void shouldIgnoreNonAuthEndpoints() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dormitories");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(rateLimiterService, never()).tryConsume(any(), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRateLimitRegisterEndpoint() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setRemoteAddr("192.168.1.50");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ConsumptionProbe probe = mock(ConsumptionProbe.class);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(2L);
        when(rateLimiterService.tryConsume(eq(AuthRateLimitEndpoint.REGISTER), eq("192.168.1.50")))
                .thenReturn(probe);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals("2", response.getHeader("X-Rate-Limit-Remaining"));
    }

    @Test
    void shouldBlockLoginWith429WhenRateLimitExceeded() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.168.1.50");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ConsumptionProbe probe = mock(ConsumptionProbe.class);
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(45_000_000_000L);
        when(rateLimiterService.tryConsume(eq(AuthRateLimitEndpoint.LOGIN), eq("192.168.1.50")))
                .thenReturn(probe);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(429, response.getStatus());
        assertEquals("46", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("Too many login attempts"));
    }

    @Test
    void shouldIgnoreForwardedHeadersWhenTrustDisabled() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");
        request.setRemoteAddr("127.0.0.1");

        assertEquals("127.0.0.1", filter.extractClientIp(request));
    }

    @Test
    void shouldUseForwardedHeadersWhenTrustEnabled() {
        filter.setTrustForwardedHeaders(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");
        request.setRemoteAddr("127.0.0.1");

        assertEquals("203.0.113.195", filter.extractClientIp(request));
    }

    @Test
    void shouldUseXRealIpWhenTrustEnabledAndNoXff() {
        filter.setTrustForwardedHeaders(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "198.51.100.42");
        request.setRemoteAddr("127.0.0.1");

        assertEquals("198.51.100.42", filter.extractClientIp(request));
    }
}
