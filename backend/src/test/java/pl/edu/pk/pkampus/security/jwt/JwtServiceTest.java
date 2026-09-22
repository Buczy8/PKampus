package pl.edu.pk.pkampus.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;
    private Dormitory testDormitory;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "0123456789012345678901234567890123456789");
        ReflectionTestUtils.setField(jwtService, "expirationMinutes", 15L);
        jwtService.init();

        testDormitory = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS-1")
                .code("DS1")
                .build();

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .passwordHash("hashedPassword")
                .firstName("Jan")
                .lastName("Kowalski")
                .phoneNumber("123456789")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(testDormitory)
                .declaredRoomNumber("204")
                .build();
    }

    @Test
    void shouldGenerateAndValidateTokenSuccessfully() {
        // Arrange & Act
        String token = jwtService.generateToken(testUser, "204");

        // Assert
        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token, testUser));
        assertEquals(testUser.getId(), jwtService.extractUserId(token));
        assertEquals("student@pk.edu.pl", jwtService.extractEmail(token));
        assertEquals("RESIDENT", jwtService.extractRole(token));
        assertEquals(testDormitory.getId(), jwtService.extractDormitoryId(token));
        assertEquals("204", jwtService.extractRoomNumber(token));
        assertNotNull(jwtService.extractExpiration(token));
        assertFalse(jwtService.isTokenExpired(token));
    }

    @Test
    void shouldGenerateTokenWithoutRoomNumber() {
        // Arrange & Act
        String token = jwtService.generateToken(testUser);

        // Assert
        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token, testUser));
        assertNull(jwtService.extractRoomNumber(token));
    }

    @Test
    void shouldGenerateTokenWhenUserHasNoDormitory() {
        // Arrange
        User userWithoutDorm = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
                .role(UserRole.SUPER_ADMIN)
                .status(UserStatus.ACTIVE)
                .dormitory(null)
                .build();

        // Act
        String token = jwtService.generateToken(userWithoutDorm);

        // Assert
        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token, userWithoutDorm));
        assertNull(jwtService.extractDormitoryId(token));
        assertEquals("SUPER_ADMIN", jwtService.extractRole(token));
    }

    @Test
    void shouldInvalidateTokenWhenUserMismatch() {
        // Arrange
        String token = jwtService.generateToken(testUser, "204");

        User otherUser = User.builder()
                .id(UUID.randomUUID())
                .email("other@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .build();

        // Act & Assert
        assertFalse(jwtService.isTokenValid(token, otherUser));
    }

    @Test
    void shouldReturnFalseForIsTokenValidWhenTokenIsMalformedOrTampered() {
        // Arrange
        String malformedToken = "eyJhbGciOiJIUzI1NiJ9.invalid-payload.signature";

        // Act & Assert
        assertFalse(jwtService.isTokenValid(malformedToken, testUser));
    }

    @Test
    void shouldReturnTrueForIsTokenExpiredWhenTokenIsMalformed() {
        // Arrange
        String malformedToken = "not-a-valid-jwt";

        // Act & Assert
        assertTrue(jwtService.isTokenExpired(malformedToken));
    }

    @Test
    void shouldReturnConfiguredExpirationMinutes() {
        // Arrange & Act
        long minutes = jwtService.getExpirationMinutes();

        // Assert
        assertEquals(15L, minutes);
    }
}
