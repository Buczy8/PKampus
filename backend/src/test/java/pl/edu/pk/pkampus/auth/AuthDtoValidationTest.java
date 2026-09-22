package pl.edu.pk.pkampus.auth;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.edu.pk.pkampus.modules.auth.dto.*;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Auth DTO validation unit tests (AAA)")
class AuthDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("RegisterRequestDto should pass when all fields are valid")
    void validRegisterRequestDto() {
        // Arrange
        RegisterRequestDto dto = RegisterRequestDto.builder()
                .email("student@pk.edu.pl")
                .password("Password123!")
                .firstName("Jan")
                .lastName("Kowalski")
                .phoneNumber("+48123456789")
                .dormitoryId(UUID.randomUUID())
                .declaredRoomNumber("101")
                .build();

        // Act
        Set<ConstraintViolation<RegisterRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("RegisterRequestDto should fail when email or password invalid")
    void invalidRegisterRequestDto() {
        // Arrange
        RegisterRequestDto dto = RegisterRequestDto.builder()
                .email("not-an-email")
                .password("short")
                .firstName("")
                .lastName("")
                .phoneNumber("123")
                .dormitoryId(null)
                .declaredRoomNumber("")
                .build();

        // Act
        Set<ConstraintViolation<RegisterRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("firstName")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("dormitoryId")));
    }

    @Test
    @DisplayName("LoginRequestDto should pass with valid email and password")
    void validLoginRequestDto() {
        // Arrange
        LoginRequestDto dto = new LoginRequestDto("test@pk.edu.pl", "SecretPass123!");

        // Act
        Set<ConstraintViolation<LoginRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("LoginRequestDto should fail when blank")
    void invalidLoginRequestDto() {
        // Arrange
        LoginRequestDto dto = new LoginRequestDto("", "");

        // Act
        Set<ConstraintViolation<LoginRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(2, violations.size());
    }

    @Test
    @DisplayName("ChangePasswordRequestDto should validate passwords")
    void changePasswordRequestDto() {
        // Arrange
        ChangePasswordRequestDto invalidDto = ChangePasswordRequestDto.builder()
                .currentPassword("")
                .newPassword("short")
                .build();

        // Act
        Set<ConstraintViolation<ChangePasswordRequestDto>> violations = validator.validate(invalidDto);

        // Assert
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("ForgotPasswordRequestDto should validate email")
    void forgotPasswordRequestDto() {
        // Arrange
        ForgotPasswordRequestDto invalidDto = new ForgotPasswordRequestDto("bad-email");

        // Act
        Set<ConstraintViolation<ForgotPasswordRequestDto>> violations = validator.validate(invalidDto);

        // Assert
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("ResetPasswordRequestDto should validate token and new password")
    void resetPasswordRequestDto() {
        // Arrange
        ResetPasswordRequestDto invalidDto = new ResetPasswordRequestDto("", "short");

        // Act
        Set<ConstraintViolation<ResetPasswordRequestDto>> violations = validator.validate(invalidDto);

        // Assert
        assertEquals(2, violations.size());
    }

    @Test
    @DisplayName("RefreshTokenRequestDto should validate token")
    void refreshTokenRequestDto() {
        // Arrange
        RefreshTokenRequestDto invalidDto = new RefreshTokenRequestDto("");

        // Act
        Set<ConstraintViolation<RefreshTokenRequestDto>> violations = validator.validate(invalidDto);

        // Assert
        assertEquals(1, violations.size());
    }
}
