package pl.edu.pk.pkampus.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import pl.edu.pk.pkampus.common.ApiResponse;

import static org.junit.jupiter.api.Assertions.*;

class JsonAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final JsonAccessDeniedHandler accessDeniedHandler = new JsonAccessDeniedHandler(objectMapper);

    @Test
    void shouldReturn403ForbiddenWithJsonApiResponse() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AccessDeniedException accessDeniedException = new AccessDeniedException("Forbidden access");

        // Act
        accessDeniedHandler.handle(request, response, accessDeniedException);

        // Assert
        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());

        ApiResponse<?> apiResponse = objectMapper.readValue(response.getContentAsString(), ApiResponse.class);
        assertFalse(apiResponse.success());
        assertEquals("Access denied: insufficient permissions", apiResponse.message());
        assertNull(apiResponse.data());
    }
}
