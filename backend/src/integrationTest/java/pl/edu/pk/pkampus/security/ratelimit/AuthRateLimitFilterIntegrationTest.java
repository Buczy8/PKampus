package pl.edu.pk.pkampus.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.edu.pk.pkampus.modules.auth.AuthService;
import pl.edu.pk.pkampus.modules.auth.dto.LoginRequestDto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.security.rate-limit.login.capacity=2",
        "app.security.rate-limit.login.duration-minutes=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AuthRateLimitFilter Live Integration Tests")
class AuthRateLimitFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("Should throttle login requests with 429 when bucket limit is exhausted")
    void shouldThrottleLoginAfterExceedingCapacity() throws Exception {
        // Arrange
        LoginRequestDto loginDto = new LoginRequestDto("test@pk.edu.pl", "ValidPassword123!");
        when(authService.login(any())).thenReturn(null);
        String body = objectMapper.writeValueAsString(loginDto);

        // Act & Assert - Request 1 (allowed, remaining = 1)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Rate-Limit-Remaining", "1"));

        // Act & Assert - Request 2 (allowed, remaining = 0)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Rate-Limit-Remaining", "0"));

        // Act & Assert - Request 3 (blocked with 429 Too Many Requests)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-Rate-Limit-Remaining", "0"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Too many login attempts")));
    }
}
