package pl.edu.pk.pkampus.modules.dormitory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.modules.dormitory.dto.DormitoryDto;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dormitories")
@RequiredArgsConstructor
@Tag(name = "Dormitories", description = "Dormitory catalog and room management")
public class DormitoryController {

    private final DormitoryService dormitoryService;

    @GetMapping
    @Operation(summary = "Get list of dormitories", description = "Returns a list of all student dormitories available in the system.")
    public ResponseEntity<ApiResponse<List<DormitoryDto>>> getDormitories() {
        return ResponseEntity.ok(ApiResponse.ok(dormitoryService.getAllDormitories()));
    }
}
