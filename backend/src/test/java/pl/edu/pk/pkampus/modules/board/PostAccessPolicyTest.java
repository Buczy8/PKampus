package pl.edu.pk.pkampus.modules.board;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
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

    @Mock
    private UserRepository userRepository;

    private PostAccessPolicy policy;

    private Dormitory dorm;
    private User resident;

    @BeforeEach
    void setUp() {
        policy = new PostAccessPolicy(postRepository, userRepository);

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

    private void givenFresh(User user) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("Should accept ACTIVE resident with dormitory")
    void requireActiveResidentSuccess() {
        // Arrange
        givenFresh(resident);

        // Act
        Dormitory result = policy.requireActiveResident(resident);

        // Assert
        assertEquals(dorm, result);
    }

    @Test
    @DisplayName("Should reject non-resident, blocked and dormitory-less accounts")
    void requireActiveResidentRejects() {
        // Arrange
        User staff = User.builder().id(UUID.randomUUID()).role(UserRole.DORM_ADMIN).status(UserStatus.ACTIVE).dormitory(dorm).build();
        User blocked = User.builder().id(UUID.randomUUID()).role(UserRole.RESIDENT).status(UserStatus.BLOCKED).dormitory(dorm).build();
        User homeless = User.builder().id(UUID.randomUUID()).role(UserRole.RESIDENT).status(UserStatus.ACTIVE).dormitory(null).build();
        givenFresh(staff);
        givenFresh(blocked);
        givenFresh(homeless);

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
                .id(UUID.randomUUID())
                .role(UserRole.RECEPTIONIST).status(UserStatus.ACTIVE).dormitory(dorm).build();
        User staffHomeless = User.builder()
                .id(UUID.randomUUID())
                .role(UserRole.DORM_ADMIN).status(UserStatus.ACTIVE).dormitory(null).build();
        givenFresh(receptionist);
        givenFresh(staffHomeless);
        givenFresh(resident);

        // Act & Assert
        assertEquals(dorm.getId(), policy.requireStaffDormitoryId(receptionist));
        assertThrows(AccessDeniedException.class, () -> policy.requireStaffDormitoryId(resident));
        assertThrows(BusinessRuleException.class, () -> policy.requireStaffDormitoryId(staffHomeless));
    }

    @Test
    @DisplayName("Should return 404 instead of NPE when viewer has no dormitory")
    void requireVisiblePostWithoutViewerDormitory() {
        // Arrange
        UUID postId = UUID.randomUUID();
        Post dormPost = Post.builder()
                .id(postId)
                .author(resident)
                .dormitory(dorm)
                .title("Lokalny")
                .content("Treść")
                .category(PostCategory.GENERAL)
                .scope(PostScope.DORMITORY)
                .status(PostStatus.ACTIVE)
                .deleted(false)
                .createdAt(Instant.now())
                .build();
        User homeless = User.builder()
                .id(UUID.randomUUID())
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(null)
                .build();
        // Homeless viewer still resolves (ACTIVE resident check is caller's job);
        // visibility fails on dormitory mismatch. requireVisiblePost takes an
        // already-resolved user, so no user reload happens here.
        when(postRepository.findByIdAndNotDeleted(postId)).thenReturn(Optional.of(dormPost));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> policy.requireVisiblePost(homeless, postId));
    }

    @Test
    @DisplayName("Should ignore stale principal fields and use reloaded state")
    void requireActiveResidentUsesReloadedState() {
        // Arrange
        User stalePrincipal = User.builder()
                .id(resident.getId())
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .build();
        User blockedFresh = User.builder()
                .id(resident.getId())
                .role(UserRole.RESIDENT)
                .status(UserStatus.BLOCKED)
                .dormitory(dorm)
                .build();
        when(userRepository.findById(resident.getId())).thenReturn(Optional.of(blockedFresh));

        // Act & Assert
        assertThrows(AccountStatusException.class,
                () -> policy.requireActiveResident(stalePrincipal));
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
