package pl.edu.pk.pkampus.security.jwt;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
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
        String token = jwtService.generateToken(testUser);

        // Assert (single parse reused for every assertion below)
        JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(token);
        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token, testUser));
        assertEquals(testUser.getId(), claims.userId());
        assertEquals("student@pk.edu.pl", claims.email());
        assertTrue(claims.expiresAt().isAfter(Instant.now()));
        assertFalse(jwtService.isTokenExpired(token));
    }

    @Test
    void shouldInvalidateTokenWhenUserMismatch() {
        // Arrange
        String token = jwtService.generateToken(testUser);

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
    void shouldRejectTokenMissingEmailClaim() {
        // Arrange: token signed with the same key but without the email claim
        SecretKeySpec key = new SecretKeySpec(
                "0123456789012345678901234567890123456789".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String token = Jwts.builder()
                .subject(testUser.getId().toString())
                .expiration(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> jwtService.parseAccessToken(token));
        assertFalse(jwtService.isTokenValid(token, testUser));
        assertTrue(jwtService.isTokenExpired(token));
    }

    @Test
    void shouldReturnConfiguredExpirationMinutes() {
        // Arrange & Act
        long minutes = jwtService.getExpirationMinutes();

        // Assert
        assertEquals(15L, minutes);
    }
}
