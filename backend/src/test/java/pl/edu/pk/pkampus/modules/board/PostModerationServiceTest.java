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

    private PostModerationService moderationService;

    private Dormitory dorm1;
    private Dormitory dorm2;
    private User residentAuthor;
    private User staffDormAdmin;
    private Post postDormitory;
    private Post postCampus;
    private UUID postId;

    @BeforeEach
    void setUp() {
        PostMapper postMapper = new PostMapper(roomAssignmentRepository, commentRepository);
        PostAccessPolicy accessPolicy = new PostAccessPolicy(postRepository);
        moderationService = new PostModerationService(postRepository, commentRepository, postMapper, accessPolicy);

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
    }

    @Test
    @DisplayName("Should list dormitory posts for staff")
    void listForStaffSuccess() {
        // Arrange
        when(postRepository.findStaffDormitoryFeed(eq(dorm1.getId()), eq(false), eq(PostCategory.BORROW_HELP), eq("ACTIVE"), any(Pageable.class)))
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
    @DisplayName("Should not allow staff to moderate CAMPUS scope posts")
    void removePostAsModeratorThrowsOnCampusScope() {
        // Arrange
        when(postRepository.findByIdAndNotDeleted(postCampus.getId())).thenReturn(Optional.of(postCampus));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> moderationService.removePostAsModerator(staffDormAdmin, postCampus.getId()));
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
        when(postRepository.findByIdAndNotDeleted(postId)).thenReturn(Optional.of(postDormitory));

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
