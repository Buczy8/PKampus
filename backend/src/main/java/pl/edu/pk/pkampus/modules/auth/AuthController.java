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
import pl.edu.pk.pkampus.modules.auth.dto.ChangePasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.ForgotPasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.LoginRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RefreshTokenRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.ResetPasswordRequestDto;
import pl.edu.pk.pkampus.modules.user.dto.UserProfileDto;
import pl.edu.pk.pkampus.modules.auth.dto.VerifyEmailResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.VerifyResetTokenResponseDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.UUID;

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
        return ResponseEntity.ok(ApiResponse.ok(response, response.getMessage()));
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
            description = "Verifies user credentials and account status (ACTIVE status required). Returns JWT token (TTL 15 min), refresh token (TTL 7 days), and user profile."
    )
    public ResponseEntity<ApiResponse<AuthResponseDto>> login(@Valid @RequestBody LoginRequestDto loginRequestDto) {
        AuthResponseDto response = authService.login(loginRequestDto);
        return ResponseEntity.ok(ApiResponse.ok(response, "Logged in successfully"));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Rotates refresh token (TTL 7 days) and issues a new access token (TTL 15 min). Invalidates old refresh token."
    )
    public ResponseEntity<ApiResponse<AuthResponseDto>> refresh(@Valid @RequestBody RefreshTokenRequestDto request) {
        AuthResponseDto response = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok(response, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "User logout",
            description = "Revokes user session, invalidates current refresh token, and adds user ID to in-memory Caffeine blacklist."
    )
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) RefreshTokenRequestDto request
    ) {
        String refreshToken = request != null ? request.getRefreshToken() : null;
        UUID userId = user != null ? user.getId() : null;
        authService.logout(userId, refreshToken);
        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out successfully"));
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

    @PostMapping("/change-password")
    @Operation(
            summary = "Change password",
            description = "Verifies the current password and sets a new one (BCrypt). "
                    + "If the account is in MUST_CHANGE_PASSWORD, transitions to ACTIVE (FR-AUTH-06)."
    )
    public ResponseEntity<ApiResponse<UserProfileDto>> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequestDto request
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User is not authenticated"));
        }
        UserProfileDto profile = authService.changePassword(user, request);
        return ResponseEntity.ok(ApiResponse.ok(profile, "Password updated successfully"));
    }

    @PostMapping("/forgot-password")
    @Operation(
            summary = "Request password reset link via email",
            description = "Generates a one-time cryptographic reset token (TTL 15 min) and dispatches reset link to the user's email (FR-AUTH-07 / ADR-07). "
                    + "Returns a generic message regardless of email existence to prevent email enumeration."
    )
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto request) {
        String message = authService.initiatePasswordReset(request);
        return ResponseEntity.ok(ApiResponse.ok(null, message));
    }

    @GetMapping("/verify-reset-token")
    @Operation(
            summary = "Verify password reset token validity",
            description = "Validates that a password reset token exists, is unexpired (TTL 15 min), and has not been used yet."
    )
    public ResponseEntity<ApiResponse<VerifyResetTokenResponseDto>> verifyResetToken(
            @RequestParam("token") String token
    ) {
        VerifyResetTokenResponseDto response = authService.verifyResetToken(token);
        return ResponseEntity.ok(ApiResponse.ok(response, "Token is valid"));
    }

    @PostMapping("/reset-password")
    @Operation(
            summary = "Reset password using token",
            description = "Validates one-time token, updates user password (BCrypt), marks token as used, and invalidates all active sessions (FR-AUTH-07 / ADR-07)."
    )
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Password has been successfully reset. You can now log in."));
    }
}
