package pl.edu.pk.pkampus.modules.board;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import pl.edu.pk.pkampus.common.PagedResponse;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.board.dto.PostDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostModerationService unit tests (AAA)")
class PostModerationServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private RoomAssignmentRepository roomAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    private PostModerationService moderationService;

    private Dormitory dorm1;
    private Dormitory dorm2;
    private User residentAuthor;
    private User staffDormAdmin;
    private User superAdmin;
    private User receptionist;
    private Post postDormitory;
    private Post postCampus;
    private UUID postId;

    @BeforeEach
    void setUp() {
        PostMapper postMapper = new PostMapper();
        BoardViewService boardViewService = new BoardViewService(roomAssignmentRepository, commentRepository, postMapper);
        PostAccessPolicy accessPolicy = new PostAccessPolicy(postRepository, userRepository);
        moderationService = new PostModerationService(postRepository, commentRepository, boardViewService, accessPolicy);

        dorm1 = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS Board 1")
                .build();

        dorm2 = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS Board 2")
                .build();

        residentAuthor = User.builder()
                .id(UUID.randomUUID())
                .email("author@pk.edu.pl")
                .firstName("Jan")
                .lastName("Kowalski")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .declaredRoomNumber("201")
                .build();

        staffDormAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
                .firstName("Kierownik")
                .lastName("DS")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .build();

        superAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("aos@pk.edu.pl")
                .firstName("Kierownik")
                .lastName("OS")
                .role(UserRole.SUPER_ADMIN)
                .status(UserStatus.ACTIVE)
                .dormitory(null)
                .build();

        receptionist = User.builder()
                .id(UUID.randomUUID())
                .email("desk@pk.edu.pl")
                .firstName("Portier")
                .lastName("DS")
                .role(UserRole.RECEPTIONIST)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .build();

        postId = UUID.randomUUID();
        postDormitory = Post.builder()
                .id(postId)
                .author(residentAuthor)
                .dormitory(dorm1)
                .title("Pożyczę czajnik")
                .content("Pilnie potrzebny na weekend")
                .category(PostCategory.BORROW_HELP)
                .scope(PostScope.DORMITORY)
                .status(PostStatus.ACTIVE)
                .deleted(false)
                .createdAt(Instant.now())
                .build();

        postCampus = Post.builder()
                .id(UUID.randomUUID())
                .author(residentAuthor)
                .dormitory(null)
                .title("Sprzedam rower")
                .content("Rower górski w dobrym stanie")
                .category(PostCategory.BUY_SELL)
                .scope(PostScope.CAMPUS)
                .status(PostStatus.ACTIVE)
                .deleted(false)
                .createdAt(Instant.now())
                .build();

        lenient().when(userRepository.findById(residentAuthor.getId()))
                .thenAnswer(inv -> Optional.of(residentAuthor));
        lenient().when(userRepository.findById(staffDormAdmin.getId()))
                .thenAnswer(inv -> Optional.of(staffDormAdmin));
        lenient().when(userRepository.findById(superAdmin.getId()))
                .thenAnswer(inv -> Optional.of(superAdmin));
        lenient().when(userRepository.findById(receptionist.getId()))
                .thenAnswer(inv -> Optional.of(receptionist));
    }

    @Test
    @DisplayName("Should list dormitory posts for staff")
    void listForStaffSuccess() {
        // Arrange
        when(postRepository.findModerationFeed(eq(dorm1.getId()), eq(PostCategory.BORROW_HELP), eq(PostStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(postDormitory)));
        when(commentRepository.countActiveByPostIds(List.of(postId)))
                .thenReturn(List.of());

        // Act
        PagedResponse<PostDto> result = moderationService.listForStaff(staffDormAdmin, PostCategory.BORROW_HELP, "ACTIVE", 0, 20);

        // Assert
        assertEquals(1, result.content().size());
        assertEquals(1, result.totalElements());
        assertEquals(postId, result.content().getFirst().id());
    }

    @Test
    @DisplayName("Should list all scopes for SUPER_ADMIN with a null dormitory filter")
    void listForStaffSuperAdminGlobal() {
        // Arrange
        when(postRepository.findModerationFeed(isNull(), eq(null), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(postDormitory, postCampus)));
        when(commentRepository.countActiveByPostIds(any()))
                .thenReturn(List.of());

        // Act
        PagedResponse<PostDto> result = moderationService.listForStaff(superAdmin, null, "ALL", 0, 20);

        // Assert
        assertEquals(2, result.content().size());
        verify(postRepository).findModerationFeed(isNull(), eq(null), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("Should remove DORMITORY post as moderator with real comment count")
    void removePostAsModeratorSuccess() {
        // Arrange
        when(postRepository.findByIdAndNotDeleted(postId)).thenReturn(Optional.of(postDormitory));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commentRepository.countActiveByPostId(postId)).thenReturn(3L);

        // Act
        PostDto result = moderationService.removePostAsModerator(staffDormAdmin, postId);

        // Assert
        assertEquals(PostStatus.REMOVED_MODERATOR, result.status());
        assertEquals(PostStatus.REMOVED_MODERATOR, postDormitory.getStatus());
        assertEquals(3, result.commentCount());
        verify(postRepository).save(postDormitory);
        verify(commentRepository).countActiveByPostId(postId);
    }

    @Test
    @DisplayName("Should allow DORM_ADMIN to moderate CAMPUS scope posts (FR-BOARD-06)")
    void removeCampusPostAsDormAdminSuccess() {
        // Arrange
        when(postRepository.findByIdAndNotDeleted(postCampus.getId())).thenReturn(Optional.of(postCampus));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commentRepository.countActiveByPostId(postCampus.getId())).thenReturn(0L);

        // Act
        PostDto result = moderationService.removePostAsModerator(staffDormAdmin, postCampus.getId());

        // Assert
        assertEquals(PostStatus.REMOVED_MODERATOR, result.status());
        verify(postRepository).save(postCampus);
    }

    @Test
    @DisplayName("Should allow SUPER_ADMIN without dormitory to moderate any post")
    void removePostAsSuperAdminGlobalSuccess() {
        // Arrange
        Post foreignPost = Post.builder()
                .id(UUID.randomUUID())
                .author(residentAuthor)
                .dormitory(dorm2)
                .title("Obcy DS")
                .content("Treść")
                .category(PostCategory.GENERAL)
                .scope(PostScope.DORMITORY)
                .status(PostStatus.ACTIVE)
                .deleted(false)
                .createdAt(Instant.now())
                .build();
        when(postRepository.findByIdAndNotDeleted(foreignPost.getId())).thenReturn(Optional.of(foreignPost));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commentRepository.countActiveByPostId(foreignPost.getId())).thenReturn(0L);

        // Act
        PostDto result = moderationService.removePostAsModerator(superAdmin, foreignPost.getId());

        // Assert
        assertEquals(PostStatus.REMOVED_MODERATOR, result.status());
        verify(postRepository).save(foreignPost);
    }

    @Test
    @DisplayName("Should hide CAMPUS posts from receptionist moderation with 404")
    void removeCampusPostAsReceptionistThrows() {
        // Arrange
        when(postRepository.findByIdAndNotDeleted(postCampus.getId())).thenReturn(Optional.of(postCampus));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> moderationService.removePostAsModerator(receptionist, postCampus.getId()));
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should hide foreign dormitory posts from DORM_ADMIN with 404")
    void removeForeignDormPostAsDormAdminThrows() {
        // Arrange
        Post foreignPost = Post.builder()
                .id(UUID.randomUUID())
                .author(residentAuthor)
                .dormitory(dorm2)
                .title("Obcy DS")
                .content("Treść")
                .category(PostCategory.GENERAL)
                .scope(PostScope.DORMITORY)
                .status(PostStatus.ACTIVE)
                .deleted(false)
                .createdAt(Instant.now())
                .build();
        when(postRepository.findByIdAndNotDeleted(foreignPost.getId())).thenReturn(Optional.of(foreignPost));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> moderationService.removePostAsModerator(staffDormAdmin, foreignPost.getId()));
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when resident tries to use staff moderation")
    void removePostThrowsWhenNotStaff() {
        // Arrange
        User resident = User.builder()
                .id(UUID.randomUUID())
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .build();
        when(userRepository.findById(resident.getId())).thenReturn(Optional.of(resident));

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> moderationService.removePostAsModerator(resident, postId));
    }

    @Test
    @DisplayName("Should remove comment as moderator")
    void removeCommentAsModeratorSuccess() {
        // Arrange
        UUID commentId = UUID.randomUUID();
        Comment comment = Comment.builder()
                .id(commentId)
                .post(postDormitory)
                .author(residentAuthor)
                .content("Inappropriate comment")
                .deleted(false)
                .build();

        when(commentRepository.findByIdWithPost(commentId)).thenReturn(Optional.of(comment));

        // Act
        moderationService.removeCommentAsModerator(staffDormAdmin, commentId);

        // Assert
        assertTrue(comment.isDeleted());
        assertNotNull(comment.getDeletedAt());
        verify(commentRepository).save(comment);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when comment already deleted")
    void removeCommentThrowsWhenAlreadyDeleted() {
        // Arrange
        UUID commentId = UUID.randomUUID();
        Comment comment = Comment.builder()
                .id(commentId)
                .post(postDormitory)
                .deleted(true)
                .build();

        when(commentRepository.findByIdWithPost(commentId)).thenReturn(Optional.of(comment));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> moderationService.removeCommentAsModerator(staffDormAdmin, commentId));
        verify(commentRepository, never()).save(any());
    }
}
