package pl.edu.pk.pkampus.modules.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
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

    @GetMapping("/card/avatar")
    @PreAuthorize("hasRole('RESIDENT')")
    @Operation(summary = "Stream the authenticated resident avatar (private MinIO proxy)")
    public ResponseEntity<InputStreamResource> getCardAvatar(@AuthenticationPrincipal User user) {
        MinioStorageService.AvatarObject avatar = profileCardService.openOwnAvatar(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .contentLength(avatar.size())
                .body(new InputStreamResource(avatar.stream()));
    }
}
