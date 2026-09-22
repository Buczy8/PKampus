package pl.edu.pk.pkampus.security.jwt;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("RefreshTokenRepository Integration Tests")
class RefreshTokenRepositoryIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DormitoryRepository dormitoryRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private JavaMailSender mailSender;

    private User testUser;
    private Dormitory testDormitory;

    @BeforeEach
    void setUp() {
        testDormitory = dormitoryRepository.save(Dormitory.builder()
                .name("DS-1 Olimp")
                .code("DS1")
                .address("ul. Skarżyńskiego 1")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(6, 0))
                .laundryClosingTime(LocalTime.of(22, 0))
                .laundrySlotDurationMinutes(60)
                .build());

        testUser = userRepository.save(User.builder()
                .email("student.jwt@pk.edu.pl")
                .passwordHash("hashedPass123!")
                .firstName("Jan")
                .lastName("Kowalski")
                .phoneNumber("+48123456789")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(testDormitory)
                .declaredRoomNumber("101")
                .build());
    }

    @Test
    @DisplayName("Should find token by hash with user and dormitory fetched")
    void shouldFindByTokenHashWithEntityGraph() {
        // Arrange
        String hash = "test-hash-unique-123";
        RefreshToken token = RefreshToken.builder()
                .user(testUser)
                .tokenHash(hash)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(token);

        // Act
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(hash);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(testUser.getId(), found.get().getUser().getId());
        assertEquals(testDormitory.getId(), found.get().getUser().getDormitory().getId());
    }

    @Test
    @DisplayName("Should revoke all active tokens for a specific user")
    void shouldRevokeAllByUserId() {
        // Arrange
        RefreshToken t1 = refreshTokenRepository.save(RefreshToken.builder()
                .user(testUser)
                .tokenHash("hash-1")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build());

        RefreshToken t2 = refreshTokenRepository.save(RefreshToken.builder()
                .user(testUser)
                .tokenHash("hash-2")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build());

        entityManager.flush();

        // Act
        refreshTokenRepository.revokeAllByUserId(testUser.getId());
        entityManager.clear();

        // Assert
        assertTrue(refreshTokenRepository.findById(t1.getId()).orElseThrow().isRevoked());
        assertTrue(refreshTokenRepository.findById(t2.getId()).orElseThrow().isRevoked());
    }

    @Test
    @DisplayName("Should accurately count active and unexpired tokens for user")
    void shouldCountActiveUnexpiredTokens() {
        // Arrange: 2 active unexpired, 1 expired, 1 revoked
        Instant now = Instant.now();

        refreshTokenRepository.save(RefreshToken.builder()
                .user(testUser)
                .tokenHash("active-1")
                .expiresAt(now.plus(2, ChronoUnit.DAYS))
                .revoked(false)
                .build());

        refreshTokenRepository.save(RefreshToken.builder()
                .user(testUser)
                .tokenHash("active-2")
                .expiresAt(now.plus(5, ChronoUnit.DAYS))
                .revoked(false)
                .build());

        refreshTokenRepository.save(RefreshToken.builder()
                .user(testUser)
                .tokenHash("expired-1")
                .expiresAt(now.minus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build());

        refreshTokenRepository.save(RefreshToken.builder()
                .user(testUser)
                .tokenHash("revoked-1")
                .expiresAt(now.plus(5, ChronoUnit.DAYS))
                .revoked(true)
                .build());

        // Act
        long count = refreshTokenRepository.countByUser_IdAndRevokedFalseAndExpiresAtAfter(testUser.getId(), now);

        // Assert
        assertEquals(2L, count);
    }

    @Test
    @DisplayName("Should find active tokens ordered by creation date ascending")
    void shouldFindActiveTokensOrderedByCreatedAtAsc() {
        // Arrange
        Instant now = Instant.now();

        RefreshToken t1 = refreshTokenRepository.save(RefreshToken.builder()
                .user(testUser)
                .tokenHash("older-hash")
                .createdAt(now.minus(2, ChronoUnit.HOURS))
                .expiresAt(now.plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build());

        RefreshToken t2 = refreshTokenRepository.save(RefreshToken.builder()
                .user(testUser)
                .tokenHash("newer-hash")
                .createdAt(now.minus(1, ChronoUnit.HOURS))
                .expiresAt(now.plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build());

        // Act
        List<RefreshToken> active = refreshTokenRepository
                .findByUser_IdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtAsc(testUser.getId(), now);

        // Assert
        assertEquals(2, active.size());
        assertEquals(t1.getId(), active.get(0).getId());
        assertEquals(t2.getId(), active.get(1).getId());
    }

    @Test
    @DisplayName("Should delete stale tokens (revoked older than threshold or expired older than threshold)")
    void shouldDeleteStaleTokens() {
        // Arrange
        Instant now = Instant.now();
        Instant revokedBefore = now.minus(1, ChronoUnit.DAYS);
        Instant expiredBefore = now.minus(7, ChronoUnit.DAYS);

        // Should be deleted: revoked 3 days ago
        RefreshToken staleRevoked = refreshTokenRepository.save(RefreshToken.builder()
                .user(testUser)
                .tokenHash("stale-revoked")
                .createdAt(now.minus(3, ChronoUnit.DAYS))
                .expiresAt(now.plus(5, ChronoUnit.DAYS))
                .revoked(true)
                .build());

        // Should be deleted: expired 8 days ago
        RefreshToken staleExpired = refreshTokenRepository.save(RefreshToken.builder()
                .user(testUser)
                .tokenHash("stale-expired")
                .createdAt(now.minus(15, ChronoUnit.DAYS))
                .expiresAt(now.minus(8, ChronoUnit.DAYS))
                .revoked(false)
                .build());

        // Should NOT be deleted: valid active token
        RefreshToken active = refreshTokenRepository.save(RefreshToken.builder()
                .user(testUser)
                .tokenHash("active-token")
                .createdAt(now)
                .expiresAt(now.plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build());

        entityManager.flush();

        // Act
        int deleted = refreshTokenRepository.deleteStaleTokens(revokedBefore, expiredBefore);
        entityManager.clear();

        // Assert
        assertEquals(2, deleted);
        assertTrue(refreshTokenRepository.findById(staleRevoked.getId()).isEmpty());
        assertTrue(refreshTokenRepository.findById(staleExpired.getId()).isEmpty());
        assertTrue(refreshTokenRepository.findById(active.getId()).isPresent());
    }
}
