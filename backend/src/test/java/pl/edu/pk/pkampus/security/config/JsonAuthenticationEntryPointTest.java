package pl.edu.pk.pkampus.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import pl.edu.pk.pkampus.common.ApiResponse;

import static org.junit.jupiter.api.Assertions.*;

class JsonAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final JsonAuthenticationEntryPoint entryPoint = new JsonAuthenticationEntryPoint(objectMapper);

    @Test
    void shouldReturn401UnauthorizedWithJsonApiResponse() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException authException = new BadCredentialsException("Bad credentials");

        // Act
        entryPoint.commence(request, response, authException);

        // Assert
        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());

        ApiResponse<?> apiResponse = objectMapper.readValue(response.getContentAsString(), ApiResponse.class);
        assertFalse(apiResponse.success());
        assertEquals("Authentication required", apiResponse.message());
        assertNull(apiResponse.data());
    }
}
