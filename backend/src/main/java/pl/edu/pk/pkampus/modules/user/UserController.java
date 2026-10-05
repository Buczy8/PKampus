package pl.edu.pk.pkampus.modules.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.modules.user.dto.UserProfileDto;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and permission management")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile", description = "Returns profile of the user associated with the Bearer authentication token.")
    public ResponseEntity<ApiResponse<UserProfileDto>> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User is not authenticated"));
        }
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserProfile(user.getId())));
    }
}
