package pl.edu.pk.pkampus.controller;

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
import pl.edu.pk.pkampus.dto.AuthResponseDto;
import pl.edu.pk.pkampus.dto.LoginRequestDto;
import pl.edu.pk.pkampus.dto.RegisterRequestDto;
import pl.edu.pk.pkampus.dto.RegisterResponseDto;
import pl.edu.pk.pkampus.dto.UserProfileDto;
import pl.edu.pk.pkampus.dto.VerifyEmailResponseDto;
import pl.edu.pk.pkampus.model.User;
import pl.edu.pk.pkampus.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpointy rejestracji, weryfikacji adresu e-mail i logowania (JWT)")
public class AuthController {

    private final AuthService authService;

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Rejestracja studenta / mieszkańca (krok 1 z 2)",
            description = "Przyjmuje formularz rejestracji oraz zdjęcie twarzy (JPEG/PNG/WebP, max 5 MB). Tworzy konto w stanie PENDING_EMAIL i wysyła podpisany link aktywacyjny (24h)."
    )
    public ResponseEntity<ApiResponse<RegisterResponseDto>> register(
            @Valid @RequestPart("data") RegisterRequestDto registerRequestDto,
            @RequestPart("photo") MultipartFile photo
    ) {
        RegisterResponseDto response = authService.registerResident(registerRequestDto, photo);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Zgłoszenie rejestracyjne zostało przyjęte"));
    }

    @GetMapping("/verify-email")
    @Operation(
            summary = "Weryfikacja adresu e-mail za pomocą podpisanego tokenu HMAC",
            description = "Weryfikuje ważność tokenu (24h) i przestawia konto w stan PENDING_APPROVAL (oczekiwanie na weryfikację meldunku przez ADS)."
    )
    public ResponseEntity<ApiResponse<VerifyEmailResponseDto>> verifyEmail(@RequestParam("token") String token) {
        VerifyEmailResponseDto response = authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.ok(response, response.getMessage()));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Logowanie użytkownika do systemu",
            description = "Weryfikuje poświadczenia i status konta (wymagany stan ACTIVE). Zwraca token JWT (TTL 15 min) z rolami i claims oraz profil użytkownika."
    )
    public ResponseEntity<ApiResponse<AuthResponseDto>> login(@Valid @RequestBody LoginRequestDto loginRequestDto) {
        AuthResponseDto response = authService.login(loginRequestDto);
        return ResponseEntity.ok(ApiResponse.ok(response, "Zalogowano pomyślnie"));
    }

    @GetMapping("/me")
    @Operation(
            summary = "Dane aktualnie zalogowanego użytkownika",
            description = "Zwraca profil użytkownika skojarzonego z tokenem uwierzytelniającym Bearer."
    )
    public ResponseEntity<ApiResponse<UserProfileDto>> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Użytkownik nie jest uwierzytelniony"));
        }
        UserProfileDto profile = authService.buildUserProfileDto(user, user.getDeclaredRoomNumber());
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }
}
