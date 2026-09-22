package pl.edu.pk.pkampus.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApiResponse unit tests")
class ApiResponseTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("ok(data) should create successful response with null message")
    void okDataOnly() {
        String payload = "test-payload";
        ApiResponse<String> response = ApiResponse.ok(payload);

        assertTrue(response.success());
        assertNull(response.message());
        assertEquals("test-payload", response.data());
        assertNotNull(response.timestamp());
        assertTrue(response.timestamp().isBefore(OffsetDateTime.now().plusSeconds(1)));
    }

    @Test
    @DisplayName("ok(data, message) should create successful response with custom message")
    void okDataWithMessage() {
        Integer payload = 42;
        String message = "Operation completed successfully";
        ApiResponse<Integer> response = ApiResponse.ok(payload, message);

        assertTrue(response.success());
        assertEquals("Operation completed successfully", response.message());
        assertEquals(42, response.data());
        assertNotNull(response.timestamp());
    }

    @Test
    @DisplayName("error(message) should create failure response with null data")
    void errorMessageOnly() {
        String errorMessage = "Something went wrong";
        ApiResponse<Void> response = ApiResponse.error(errorMessage);

        assertFalse(response.success());
        assertEquals("Something went wrong", response.message());
        assertNull(response.data());
        assertNotNull(response.timestamp());
    }

    @Test
    @DisplayName("Serialization should exclude null fields (NON_NULL inclusion)")
    void jsonSerializationExcludesNulls() throws Exception {
        ApiResponse<Void> errorResponse = ApiResponse.error("Resource not found");
        String json = objectMapper.writeValueAsString(errorResponse);

        assertFalse(json.contains("\"data\""));
        assertTrue(json.contains("\"success\":false"));
        assertTrue(json.contains("\"message\":\"Resource not found\""));
        assertTrue(json.contains("\"timestamp\""));

        ApiResponse<String> okNoMessage = ApiResponse.ok("data-value");
        String okJson = objectMapper.writeValueAsString(okNoMessage);

        assertFalse(okJson.contains("\"message\""));
        assertTrue(okJson.contains("\"success\":true"));
        assertTrue(okJson.contains("\"data\":\"data-value\""));
    }

    @Test
    @DisplayName("Complex object data should be serialized properly")
    void complexDataSerialization() throws Exception {
        Map<String, Object> data = Map.of("id", 1, "name", "John");
        ApiResponse<Map<String, Object>> response = ApiResponse.ok(data, "Fetched user");

        String json = objectMapper.writeValueAsString(response);

        assertTrue(json.contains("\"id\":1"));
        assertTrue(json.contains("\"name\":\"John\""));
        assertTrue(json.contains("\"message\":\"Fetched user\""));
        assertTrue(json.contains("\"success\":true"));
    }
}
