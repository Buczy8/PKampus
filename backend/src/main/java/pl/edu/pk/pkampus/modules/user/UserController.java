package pl.edu.pk.pkampus.modules.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.modules.user.dto.UserProfileDto;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Zarządzanie użytkownikami i profilami")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Pobranie profilu zalogowanego użytkownika", description = "Zwraca profil użytkownika skojarzonego z tokenem uwierzytelniającym Bearer.")
    public ResponseEntity<ApiResponse<UserProfileDto>> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Użytkownik nie jest uwierzytelniony"));
        }
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserProfile(user.getId())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Pobranie profilu użytkownika po ID", description = "Zwraca szczegółowe dane profilowe wskazanego użytkownika.")
    public ResponseEntity<ApiResponse<UserProfileDto>> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserProfile(id)));
    }
}
