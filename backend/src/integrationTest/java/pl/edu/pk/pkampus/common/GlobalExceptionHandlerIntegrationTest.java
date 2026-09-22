package pl.edu.pk.pkampus.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import pl.edu.pk.pkampus.common.exception.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerIntegrationTest.TestExceptionController.class)
@DisplayName("GlobalExceptionHandler integration tests")
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    record ValidatedTestDto(
            @NotBlank(message = "Title must not be blank")
            String title
    ) {}

    @TestConfiguration
    @RestController
    @RequestMapping("/api/test/exceptions")
    static class TestExceptionController {

        @PostMapping("/validation")
        public String triggerValidation(@Valid @RequestBody ValidatedTestDto dto) {
            return "OK";
        }

        @GetMapping("/business-rule")
        public String triggerBusinessRule() {
            throw new BusinessRuleException("Maximum reservations reached");
        }

        @GetMapping("/not-found")
        public String triggerNotFound() {
            throw new ResourceNotFoundException("Laundry machine not found");
        }

        @GetMapping("/slot-conflict")
        public String triggerSlotConflict() {
            throw new SlotConflictException("Slot overlapping with another user");
        }

        @GetMapping("/rate-limit")
        public String triggerRateLimit() {
            throw new RateLimitExceededException("Too many requests, try later", 60L);
        }

        @GetMapping("/token-invalid")
        public String triggerTokenInvalid() {
            throw new EmailVerificationTokenInvalidException("Activation token expired");
        }

        @GetMapping("/illegal-argument")
        public String triggerIllegalArgument() {
            throw new IllegalArgumentException("Invalid format provided");
        }
    }

    @Test
    @DisplayName("Validation error should return 400 Bad Request with field error map")
    void validationErrorIntegration() throws Exception {
        mockMvc.perform(post("/api/test/exceptions/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.data.title").value("Title must not be blank"));
    }

    @Test
    @DisplayName("BusinessRuleException should return 422 Unprocessable Entity")
    void businessRuleIntegration() throws Exception {
        mockMvc.perform(get("/api/test/exceptions/business-rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Maximum reservations reached"));
    }

    @Test
    @DisplayName("ResourceNotFoundException should return 404 Not Found")
    void resourceNotFoundIntegration() throws Exception {
        mockMvc.perform(get("/api/test/exceptions/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Laundry machine not found"));
    }

    @Test
    @DisplayName("SlotConflictException should return 409 Conflict")
    void slotConflictIntegration() throws Exception {
        mockMvc.perform(get("/api/test/exceptions/slot-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Slot overlapping with another user"));
    }

    @Test
    @DisplayName("RateLimitExceededException should return 429 Too Many Requests with headers")
    void rateLimitIntegration() throws Exception {
        mockMvc.perform(get("/api/test/exceptions/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(header().string("X-Rate-Limit-Retry-After-Seconds", "60"))
                .andExpect(header().string("X-Rate-Limit-Remaining", "0"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Too many requests, try later"));
    }

    @Test
    @DisplayName("EmailVerificationTokenInvalidException should return 410 Gone")
    void emailTokenInvalidIntegration() throws Exception {
        mockMvc.perform(get("/api/test/exceptions/token-invalid"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Activation token expired"));
    }

    @Test
    @DisplayName("IllegalArgumentException should return 400 Bad Request")
    void illegalArgumentIntegration() throws Exception {
        mockMvc.perform(get("/api/test/exceptions/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid format provided"));
    }
}
