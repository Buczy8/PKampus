package pl.edu.pk.pkampus.modules.issues;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.modules.issues.dto.CreateIssueRequestDto;
import pl.edu.pk.pkampus.modules.issues.dto.IssueDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Issues", description = "Resident maintenance issue reports (FR-ISSUE-01 / FR-ISSUE-05)")
public class IssueController {

    private final IssueService issueService;

    @GetMapping("/me")
    @Operation(summary = "List my reported issues")
    public ResponseEntity<ApiResponse<List<IssueDto>>> myIssues(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(issueService.listMyIssues(user)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a maintenance issue (optional photo)")
    public ResponseEntity<ApiResponse<IssueDto>> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestPart("data") CreateIssueRequestDto data,
            @RequestPart(value = "photo", required = false) MultipartFile photo
    ) {
        IssueDto created = issueService.createIssue(user, data, photo);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Issue reported"));
    }

    @GetMapping("/{id}/photo")
    @Operation(summary = "Redirect to a short-lived presigned URL for the issue photo")
    public ResponseEntity<?> photo(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        String url = issueService.getOwnIssuePhotoPresignedUrl(user, id);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .body(ApiResponse.ok(Map.of("url", url)));
    }
}
