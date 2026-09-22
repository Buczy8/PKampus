package pl.edu.pk.pkampus.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OpenApiConfig unit tests")
class OpenApiConfigTest {

    @Test
    @DisplayName("customOpenAPI() should configure title, version, contact and JWT bearer scheme")
    void customOpenAPIConfiguration() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.customOpenAPI();

        assertNotNull(openAPI);
        assertNotNull(openAPI.getInfo());
        assertEquals("PKampus REST API", openAPI.getInfo().getTitle());
        assertEquals("1.0.0", openAPI.getInfo().getVersion());
        assertNotNull(openAPI.getInfo().getContact());
        assertEquals("PKampus Team", openAPI.getInfo().getContact().getName());
        assertEquals("admin@pkampus.pk.edu.pl", openAPI.getInfo().getContact().getEmail());

        assertNotNull(openAPI.getSecurity());
        assertFalse(openAPI.getSecurity().isEmpty());
        SecurityRequirement secReq = openAPI.getSecurity().getFirst();
        assertTrue(secReq.containsKey("bearerAuth"));

        assertNotNull(openAPI.getComponents());
        SecurityScheme scheme = openAPI.getComponents().getSecuritySchemes().get("bearerAuth");
        assertNotNull(scheme);
        assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
        assertEquals("bearer", scheme.getScheme());
        assertEquals("JWT", scheme.getBearerFormat());
    }
}
