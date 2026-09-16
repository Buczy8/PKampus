package pl.edu.pk.pkampus.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health & Diagnostics", description = "Endpoints for service health checks and status monitoring")
public class HealthController {

    @GetMapping
    @Operation(summary = "Check service health", description = "Returns the operational status and metadata of the PKampus REST API")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkHealth() {
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of(
                        "status", "UP",
                        "system", "PKampus REST API",
                        "version", "0.0.1-SNAPSHOT",
                        "serverTime", OffsetDateTime.now()
                ),
                "PKampus service is operational"
        ));
    }
}
