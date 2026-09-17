package pl.edu.pk.pkampus.modules.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.modules.auth.dto.AuthResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.LoginRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterResponseDto;
import pl.edu.pk.pkampus.modules.user.dto.UserProfileDto;
import pl.edu.pk.pkampus.modules.auth.dto.VerifyEmailResponseDto;
import pl.edu.pk.pkampus.modules.user.User;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor

@Tag(name = "Authentication", description = "Registration, email verification, and authentication (JWT) endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Resident registration (step 1 of 2)",
            description = "Accepts registration data and facial photo (JPEG/PNG/WebP, max 5 MB). Creates account in PENDING_EMAIL status and sends signed activation link (24h)."
    )
    public ResponseEntity<ApiResponse<RegisterResponseDto>> register(
            @Valid @RequestPart("data") RegisterRequestDto registerRequestDto,
            @RequestPart("photo") MultipartFile photo
    ) {
        RegisterResponseDto response = authService.registerResident(registerRequestDto, photo);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, response.getMessage()));
    }

    @GetMapping("/verify-email")
    @Operation(
            summary = "Email address verification via HMAC signed token",
            description = "Verifies token validity (24h) and transitions account to PENDING_APPROVAL status (awaiting residency approval by dormitory administration)."
    )
    public ResponseEntity<ApiResponse<VerifyEmailResponseDto>> verifyEmail(@RequestParam("token") String token) {
        VerifyEmailResponseDto response = authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.ok(response, response.getMessage()));
    }

    @PostMapping("/login")
    @Operation(
            summary = "User authentication (login)",
            description = "Verifies user credentials and account status (ACTIVE status required). Returns JWT token (TTL 15 min) and user profile."
    )
    public ResponseEntity<ApiResponse<AuthResponseDto>> login(@Valid @RequestBody LoginRequestDto loginRequestDto) {
        AuthResponseDto response = authService.login(loginRequestDto);
        return ResponseEntity.ok(ApiResponse.ok(response, "Logged in successfully"));
    }

    @GetMapping("/me")
    @Operation(
            summary = "Current authenticated user profile",
            description = "Returns profile of the user associated with the Bearer authentication token."
    )
    public ResponseEntity<ApiResponse<UserProfileDto>> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User is not authenticated"));
        }
        UserProfileDto profile = authService.getCurrentUserProfile(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }
}
