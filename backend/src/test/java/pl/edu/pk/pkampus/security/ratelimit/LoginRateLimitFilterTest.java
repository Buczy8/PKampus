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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginRateLimitFilterTest {

    @Mock
    private LoginRateLimiterService rateLimiterService;

    @Mock
    private FilterChain filterChain;

    private LoginRateLimitFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        filter = new LoginRateLimitFilter(rateLimiterService, objectMapper);
    }

    @Test
    void shouldIgnoreNonLoginEndpoints() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dormitories");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(rateLimiterService, never()).tryConsume(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldIgnoreGetLoginRequests() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(rateLimiterService, never()).tryConsume(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAllowLoginWhenTokensAreAvailable() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.168.1.50");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ConsumptionProbe probe = mock(ConsumptionProbe.class);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(4L);
        when(rateLimiterService.tryConsume("192.168.1.50")).thenReturn(probe);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals("4", response.getHeader("X-Rate-Limit-Remaining"));
    }

    @Test
    void shouldBlockLoginWith429WhenRateLimitExceeded() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.168.1.50");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ConsumptionProbe probe = mock(ConsumptionProbe.class);
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(45_000_000_000L); // 45 seconds
        when(rateLimiterService.tryConsume("192.168.1.50")).thenReturn(probe);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(429, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertEquals("46", response.getHeader("Retry-After"));
        assertEquals("0", response.getHeader("X-Rate-Limit-Remaining"));
        assertTrue(response.getContentAsString().contains("Too many login attempts"));
    }

    @Test
    void shouldExtractClientIpFromXForwardedForHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");
        request.setRemoteAddr("127.0.0.1");

        String ip = filter.extractClientIp(request);
        assertEquals("203.0.113.195", ip);
    }

    @Test
    void shouldExtractClientIpFromXRealIpHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "198.51.100.42");
        request.setRemoteAddr("127.0.0.1");

        String ip = filter.extractClientIp(request);
        assertEquals("198.51.100.42", ip);
    }
}
