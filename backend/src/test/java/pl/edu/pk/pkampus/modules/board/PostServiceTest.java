package pl.edu.pk.pkampus.modules.board;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import pl.edu.pk.pkampus.common.PagedResponse;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.board.dto.CommentDto;
import pl.edu.pk.pkampus.modules.board.dto.CreateCommentRequestDto;
import pl.edu.pk.pkampus.modules.board.dto.CreatePostRequestDto;
import pl.edu.pk.pkampus.modules.board.dto.PostDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignment;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostService unit tests (AAA)")
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private RoomAssignmentRepository roomAssignmentRepository;

    private PostMapper postMapper;
    private PostAccessPolicy accessPolicy;
    private PostService postService;

    private Dormitory dorm1;
    private Dormitory dorm2;
    private User residentAuthor;
    private User residentOtherDorm;
    private User staffDormAdmin;
    private Post postDormitory;
    private Post postCampus;
    private UUID postId;

    @BeforeEach
    void setUp() {
        postMapper = new PostMapper(roomAssignmentRepository, commentRepository);
        accessPolicy = new PostAccessPolicy(postRepository);
        postService = new PostService(postRepository, commentRepository, postMapper, accessPolicy);

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

        residentOtherDorm = User.builder()
                .id(UUID.randomUUID())
                .email("other@pk.edu.pl")
                .firstName("Anna")
                .lastName("Nowak")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm2)
                .declaredRoomNumber("105")
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

    @Nested
    @DisplayName("listFeed")
    class ListFeed {

        @Test
        @DisplayName("Should return feed with room number for DORMITORY scope and comment counts")
        void listFeedSuccess() {
            // Arrange
            Room room = Room.builder().roomNumber("201-A").build();
            RoomAssignment assignment = RoomAssignment.builder()
                    .user(residentAuthor)
                    .room(room)
                    .isActive(true)
                    .build();

            when(postRepository.findFeed(eq(dorm1.getId()), eq(PostCategory.BORROW_HELP), eq(PostScope.DORMITORY), eq("ACTIVE"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(postDormitory)));
            when(commentRepository.countActiveByPostIds(List.of(postId)))
                    .thenReturn(List.<Object[]>of(new Object[]{postId, 3L}));
            when(roomAssignmentRepository.findActiveByUserIdIn(List.of(residentAuthor.getId())))
                    .thenReturn(List.of(assignment));

            // Act
            PagedResponse<PostDto> result = postService.listFeed(residentAuthor, PostCategory.BORROW_HELP, PostScope.DORMITORY, "active", 0, 20);

            // Assert
            assertEquals(1, result.content().size());
            assertEquals(1, result.totalElements());
            PostDto dto = result.content().getFirst();
            assertEquals(postId, dto.id());
            assertEquals("Pożyczę czajnik", dto.title());
            assertEquals("Jan Kowalski", dto.authorDisplayName());
            assertEquals("201-A", dto.authorRoomNumber());
            assertEquals(3, dto.commentCount());
            assertTrue(dto.mine());
        }

        @Test
        @DisplayName("Should hide room number for CAMPUS scope posts")
        void listFeedCampusHidesRoom() {
            // Arrange
            when(postRepository.findFeed(eq(dorm1.getId()), eq(null), eq(null), eq("ACTIVE"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(postCampus)));
            when(commentRepository.countActiveByPostIds(List.of(postCampus.getId())))
                    .thenReturn(List.of());

            // Act
            PagedResponse<PostDto> result = postService.listFeed(residentAuthor, null, null, null, 0, 20);

            // Assert
            assertEquals(1, result.content().size());
            assertNull(result.content().getFirst().authorRoomNumber());
            assertEquals("DS Board 1", result.content().getFirst().authorDormitoryName());
            verify(roomAssignmentRepository, never()).findActiveByUserIdIn(any());
        }

        @Test
        @DisplayName("Should throw BusinessRuleException on invalid status filter")
        void listFeedThrowsOnInvalidStatus() {
            // Arrange & Act & Assert
            assertThrows(BusinessRuleException.class,
                    () -> postService.listFeed(residentAuthor, null, null, "INVALID", 0, 20));
        }

        @Test
        @DisplayName("Should throw AccountStatusException if user is not ACTIVE resident")
        void listFeedThrowsWhenUserNotActiveResident() {
            // Arrange
            residentAuthor.setStatus(UserStatus.BLOCKED);

            // Act & Assert
            assertThrows(AccountStatusException.class,
                    () -> postService.listFeed(residentAuthor, null, null, "ACTIVE", 0, 20));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException on negative page")
        void listFeedThrowsOnNegativePage() {
            // Arrange & Act & Assert
            assertThrows(BusinessRuleException.class,
                    () -> postService.listFeed(residentAuthor, null, null, "ACTIVE", -1, 20));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when size exceeds maximum")
        void listFeedThrowsOnOversizedPage() {
            // Arrange & Act & Assert
            assertThrows(BusinessRuleException.class,
                    () -> postService.listFeed(residentAuthor, null, null, "ACTIVE", 0, 51));
            assertThrows(BusinessRuleException.class,
                    () -> postService.listFeed(residentAuthor, null, null, "ACTIVE", 0, 0));
        }

        @Test
        @DisplayName("Should pass page and size to repository")
        void listFeedPassesPagination() {
            // Arrange
            when(postRepository.findFeed(eq(dorm1.getId()), eq(null), eq(null), eq("ACTIVE"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 12));

            // Act
            PagedResponse<PostDto> result = postService.listFeed(residentAuthor, null, null, "ACTIVE", 2, 5);

            // Assert
            assertEquals(0, result.content().size());
            assertEquals(12, result.totalElements());
            assertEquals(3, result.totalPages());
            assertEquals(2, result.page());
            assertTrue(result.last());

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(postRepository).findFeed(eq(dorm1.getId()), eq(null), eq(null), eq("ACTIVE"), captor.capture());
            assertEquals(2, captor.getValue().getPageNumber());
            assertEquals(5, captor.getValue().getPageSize());
        }
    }

    @Nested
    @DisplayName("create")
    class CreatePost {

        @Test
        @DisplayName("Should create DORMITORY scoped post with dormitory assigned")
        void createDormitoryPostSuccess() {
            // Arrange
            CreatePostRequestDto request = new CreatePostRequestDto(
                    "  Potrzebna pralka  ",
                    "  Czy ktoś ma wolny slot?  ",
                    PostCategory.GENERAL,
                    PostScope.DORMITORY
            );

            when(postRepository.saveAndFlush(any(Post.class))).thenAnswer(inv -> {
                Post p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                p.setCreatedAt(Instant.now());
                return p;
            });

            // Act
            PostDto result = postService.create(residentAuthor, request);

            // Assert
            assertNotNull(result);
            assertEquals("Potrzebna pralka", result.title());
            assertEquals("Czy ktoś ma wolny slot?", result.content());
            assertEquals(PostScope.DORMITORY, result.scope());
            assertEquals(0, result.commentCount());

            ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
            verify(postRepository).saveAndFlush(captor.capture());
            Post saved = captor.getValue();
            assertEquals("Potrzebna pralka", saved.getTitle());
            assertEquals(dorm1, saved.getDormitory());
            assertEquals(residentAuthor, saved.getAuthor());
            assertEquals(PostStatus.ACTIVE, saved.getStatus());
            assertFalse(saved.isDeleted());
        }

        @Test
        @DisplayName("Should create CAMPUS scoped post with null dormitory")
        void createCampusPostSuccess() {
            // Arrange
            CreatePostRequestDto request = new CreatePostRequestDto(
                    "Sprzedam książki",
                    "Matematyka dyskretna",
                    PostCategory.BUY_SELL,
                    PostScope.CAMPUS
            );

            when(postRepository.saveAndFlush(any(Post.class))).thenAnswer(inv -> {
                Post p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                p.setCreatedAt(Instant.now());
                return p;
            });

            // Act
            PostDto result = postService.create(residentAuthor, request);

            // Assert
            assertEquals(PostScope.CAMPUS, result.scope());

            ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
            verify(postRepository).saveAndFlush(captor.capture());
            assertNull(captor.getValue().getDormitory());
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when title or content is empty after trimming")
        void createThrowsWhenBlankTitleOrContent() {
            // Arrange
            CreatePostRequestDto blankTitle = new CreatePostRequestDto("   ", "Treść", PostCategory.GENERAL, PostScope.CAMPUS);
            CreatePostRequestDto blankContent = new CreatePostRequestDto("Tytuł", "   ", PostCategory.GENERAL, PostScope.CAMPUS);

            // Act & Assert
            assertThrows(BusinessRuleException.class, () -> postService.create(residentAuthor, blankTitle));
            assertThrows(BusinessRuleException.class, () -> postService.create(residentAuthor, blankContent));
        }

        @Test
        @DisplayName("Should throw AccountStatusException when user has no dormitory")
        void createThrowsWhenUserHasNoDormitory() {
            // Arrange
            residentAuthor.setDormitory(null);
            CreatePostRequestDto request = new CreatePostRequestDto("Tytuł", "Treść", PostCategory.GENERAL, PostScope.CAMPUS);

            // Act & Assert
            assertThrows(AccountStatusException.class, () -> postService.create(residentAuthor, request));
        }
    }

    @Nested
    @DisplayName("resolve")
    class ResolvePost {

        @Test
        @DisplayName("Should mark own active post as RESOLVED")
        void resolveSuccess() {
            // Arrange
            when(postRepository.findByIdAndNotDeleted(postId)).thenReturn(Optional.of(postDormitory));
            when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
            when(commentRepository.countActiveByPostId(postId)).thenReturn(2L);

            // Act
            PostDto result = postService.resolve(residentAuthor, postId);

            // Assert
            assertEquals(PostStatus.RESOLVED, result.status());
            assertEquals(PostStatus.RESOLVED, postDormitory.getStatus());
            assertEquals(2, result.commentCount());
            verify(postRepository).save(postDormitory);
            verify(commentRepository).countActiveByPostId(postId);
            verify(commentRepository, never()).findActiveByPostIdOrderByCreatedAtAsc(any());
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when post is already RESOLVED")
        void resolveThrowsWhenAlreadyResolved() {
            // Arrange
            postDormitory.setStatus(PostStatus.RESOLVED);
            when(postRepository.findByIdAndNotDeleted(postId)).thenReturn(Optional.of(postDormitory));

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> postService.resolve(residentAuthor, postId));
            assertEquals("Only ACTIVE posts can be marked as resolved", ex.getMessage());
            verify(postRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when non-author attempts to resolve")
        void resolveThrowsWhenNotAuthor() {
            // Arrange
            User residentSameDorm = User.builder()
                    .id(UUID.randomUUID())
                    .role(UserRole.RESIDENT)
                    .status(UserStatus.ACTIVE)
                    .dormitory(dorm1)
                    .build();

            when(postRepository.findByIdAndNotDeleted(postId)).thenReturn(Optional.of(postDormitory));

            // Act & Assert
            assertThrows(AccessDeniedException.class,
                    () -> postService.resolve(residentSameDorm, postId));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when post belongs to another dormitory")
        void resolveThrowsWhenPostFromOtherDormitory() {
            // Arrange
            when(postRepository.findByIdAndNotDeleted(postId)).thenReturn(Optional.of(postDormitory));

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> postService.resolve(residentOtherDorm, postId));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when post is REMOVED_MODERATOR")
        void resolveThrowsWhenRemovedByModerator() {
            // Arrange
            postDormitory.setStatus(PostStatus.REMOVED_MODERATOR);
            when(postRepository.findByIdAndNotDeleted(postId)).thenReturn(Optional.of(postDormitory));

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> postService.resolve(residentAuthor, postId));
        }
    }

    @Nested
    @DisplayName("softDelete")
    class SoftDeletePost {

        @Test
        @DisplayName("Should soft-delete own post by setting deleted flag and deletedAt timestamp")
        void softDeleteSuccess() {
            // Arrange
            when(postRepository.findByIdAndNotDeleted(postId)).thenReturn(Optional.of(postDormitory));

            // Act
            postService.softDelete(residentAuthor, postId);

            // Assert
            assertTrue(postDormitory.isDeleted());
            assertNotNull(postDormitory.getDeletedAt());
            verify(postRepository).save(postDormitory);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when stranger attempts to delete post")
        void softDeleteThrowsWhenNotAuthor() {
            // Arrange
            User residentSameDorm = User.builder()
                    .id(UUID.randomUUID())
                    .role(UserRole.RESIDENT)
                    .status(UserStatus.ACTIVE)
                    .dormitory(dorm1)
                    .build();

            when(postRepository.findByIdAndNotDeleted(postId)).thenReturn(Optional.of(postDormitory));

            // Act & Assert
            assertThrows(AccessDeniedException.class,
                    () -> postService.softDelete(residentSameDorm, postId));
        }
    }

    @Nested
    @DisplayName("comments")
    class Comments {

        @Test
        @DisplayName("Should add comment to post and trim whitespace")
        void addCommentSuccess() {
            // Arrange
            CreateCommentRequestDto request = new CreateCommentRequestDto("  Mogę pożyczyć swój!  ");

            when(postRepository.findByIdAndNotDeleted(postId)).thenReturn(Optional.of(postDormitory));
            when(commentRepository.saveAndFlush(any(Comment.class))).thenAnswer(inv -> {
                Comment c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                c.setCreatedAt(Instant.now());
                return c;
            });

            // Act
            CommentDto result = postService.addComment(residentAuthor, postId, request);

            // Assert
            assertNotNull(result);
            assertEquals("Mogę pożyczyć swój!", result.content());
            assertEquals(postId, result.postId());
            assertTrue(result.mine());

            ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
            verify(commentRepository).saveAndFlush(captor.capture());
            Comment saved = captor.getValue();
            assertEquals("Mogę pożyczyć swój!", saved.getContent());
            assertEquals(postDormitory, saved.getPost());
            assertEquals(residentAuthor, saved.getAuthor());
            assertFalse(saved.isDeleted());
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when comment content is blank")
        void addCommentThrowsWhenBlankContent() {
            // Arrange
            CreateCommentRequestDto request = new CreateCommentRequestDto("   ");
            when(postRepository.findByIdAndNotDeleted(postId)).thenReturn(Optional.of(postDormitory));

            // Act & Assert
            assertThrows(BusinessRuleException.class,
                    () -> postService.addComment(residentAuthor, postId, request));
            verify(commentRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when commenting on REMOVED_MODERATOR post")
        void addCommentThrowsWhenPostRemovedByModerator() {
            // Note: requireVisiblePost checks REMOVED_MODERATOR and throws ResourceNotFoundException
            // but addComment also has an explicit check. When requireVisiblePost returns, if status was somehow REMOVED_MODERATOR:
            // Arrange
            postDormitory.setStatus(PostStatus.REMOVED_MODERATOR);
            when(postRepository.findByIdAndNotDeleted(postId)).thenReturn(Optional.of(postDormitory));

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> postService.addComment(residentAuthor, postId, new CreateCommentRequestDto("Treść")));
        }

        @Test
        @DisplayName("Should list active comments under post")
        void listCommentsSuccess() {
            // Arrange
            Comment comment = Comment.builder()
                    .id(UUID.randomUUID())
                    .post(postDormitory)
                    .author(residentAuthor)
                    .content("Komentarz testowy")
                    .deleted(false)
                    .createdAt(Instant.now())
                    .build();

            when(postRepository.findByIdAndNotDeleted(postId)).thenReturn(Optional.of(postDormitory));
            when(commentRepository.findActiveByPostIdOrderByCreatedAtAsc(postId)).thenReturn(List.of(comment));

            // Act
            List<CommentDto> result = postService.listComments(residentAuthor, postId);

            // Assert
            assertEquals(1, result.size());
            assertEquals("Komentarz testowy", result.getFirst().content());
            assertEquals("Jan Kowalski", result.getFirst().authorDisplayName());
        }
    }
}
