package pl.edu.pk.pkampus.modules.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.modules.profile.dto.ResidentCardDto;
import pl.edu.pk.pkampus.modules.user.User;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Resident Card", description = "Virtual resident ID card (FR-CARD)")
public class ProfileController {

    private final ProfileCardService profileCardService;

    @GetMapping("/card")
    @PreAuthorize("hasRole('RESIDENT')")
    @Operation(summary = "Active resident identification card with daily verification code")
    public ResponseEntity<ApiResponse<ResidentCardDto>> getCard(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(profileCardService.getResidentCard(user)));
    }
}
