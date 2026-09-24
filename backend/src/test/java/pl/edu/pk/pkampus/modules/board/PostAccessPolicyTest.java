package pl.edu.pk.pkampus.modules.board;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostAccessPolicy unit tests (AAA)")
class PostAccessPolicyTest {

    @Mock
    private PostRepository postRepository;

    private PostAccessPolicy policy;

    private Dormitory dorm;
    private User resident;

    @BeforeEach
    void setUp() {
        policy = new PostAccessPolicy(postRepository);

        dorm = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS Policy")
                .build();

        resident = User.builder()
                .id(UUID.randomUUID())
                .email("resident@pk.edu.pl")
                .firstName("Jan")
                .lastName("Kowalski")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .declaredRoomNumber("101")
                .build();
    }

    @Test
    @DisplayName("Should accept ACTIVE resident with dormitory")
    void requireActiveResidentSuccess() {
        // Arrange — setUp

        // Act
        Dormitory result = policy.requireActiveResident(resident);

        // Assert
        assertEquals(dorm, result);
    }

    @Test
    @DisplayName("Should reject non-resident, blocked and dormitory-less accounts")
    void requireActiveResidentRejects() {
        // Arrange
        User staff = User.builder().role(UserRole.DORM_ADMIN).status(UserStatus.ACTIVE).dormitory(dorm).build();
        User blocked = User.builder().role(UserRole.RESIDENT).status(UserStatus.BLOCKED).dormitory(dorm).build();
        User homeless = User.builder().role(UserRole.RESIDENT).status(UserStatus.ACTIVE).dormitory(null).build();

        // Act & Assert
        assertThrows(AccountStatusException.class, () -> policy.requireActiveResident(staff));
        assertThrows(AccountStatusException.class, () -> policy.requireActiveResident(blocked));
        assertThrows(AccountStatusException.class, () -> policy.requireActiveResident(homeless));
    }

    @Test
    @DisplayName("Should accept receptionist and reject resident without dormitory check")
    void requireStaffDormitoryId() {
        // Arrange
        User receptionist = User.builder()
                .role(UserRole.RECEPTIONIST).status(UserStatus.ACTIVE).dormitory(dorm).build();
        User staffHomeless = User.builder()
                .role(UserRole.DORM_ADMIN).status(UserStatus.ACTIVE).dormitory(null).build();

        // Act & Assert
        assertEquals(dorm.getId(), policy.requireStaffDormitoryId(receptionist));
        assertThrows(AccessDeniedException.class, () -> policy.requireStaffDormitoryId(resident));
        assertThrows(BusinessRuleException.class, () -> policy.requireStaffDormitoryId(staffHomeless));
    }

    @Test
    @DisplayName("Should normalize status filter case-insensitively with ACTIVE default")
    void normalizeStatusFilter() {
        // Arrange — setUp

        // Act & Assert
        assertEquals("ACTIVE", policy.normalizeStatusFilter(null));
        assertEquals("ACTIVE", policy.normalizeStatusFilter("  "));
        assertEquals("RESOLVED", policy.normalizeStatusFilter("resolved"));
        assertEquals("ALL", policy.normalizeStatusFilter(" all "));
        assertThrows(BusinessRuleException.class, () -> policy.normalizeStatusFilter("DELETED"));
    }

    @Test
    @DisplayName("Should build createdAt-descending pageable and reject out-of-range input")
    void feedPageable() {
        // Arrange — setUp

        // Act
        PageRequest result = policy.feedPageable(2, 25);

        // Assert
        assertEquals(2, result.getPageNumber());
        assertEquals(25, result.getPageSize());
        assertTrue(result.getSort().getOrderFor("createdAt").isDescending());

        assertThrows(BusinessRuleException.class, () -> policy.feedPageable(-1, 20));
        assertThrows(BusinessRuleException.class, () -> policy.feedPageable(0, 0));
        assertThrows(BusinessRuleException.class,
                () -> policy.feedPageable(0, PostAccessPolicy.MAX_FEED_PAGE_SIZE + 1));
    }

    @Test
    @DisplayName("Should hide CAMPUS posts from staff moderation with 404")
    void requireStaffModeratablePostRejectsCampus() {
        // Arrange
        UUID postId = UUID.randomUUID();
        Post campus = Post.builder()
                .id(postId)
                .author(resident)
                .dormitory(null)
                .title("Kampus")
                .content("Treść")
                .category(PostCategory.GENERAL)
                .scope(PostScope.CAMPUS)
                .status(PostStatus.ACTIVE)
                .deleted(false)
                .createdAt(Instant.now())
                .build();
        when(postRepository.findByIdAndNotDeleted(postId)).thenReturn(Optional.of(campus));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> policy.requireStaffModeratablePost(postId, dorm.getId()));
    }
}
