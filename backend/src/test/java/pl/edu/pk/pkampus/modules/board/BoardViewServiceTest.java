package pl.edu.pk.pkampus.modules.board;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.modules.board.dto.CommentDto;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BoardViewService unit tests (AAA)")
class BoardViewServiceTest {

    @Mock
    private RoomAssignmentRepository roomAssignmentRepository;

    @Mock
    private CommentRepository commentRepository;

    private PostMapper postMapper;
    private BoardViewService boardViewService;

    private Dormitory dorm;
    private User authorWithAssignment;
    private User authorWithoutAssignment;
    private User viewer;

    @BeforeEach
    void setUp() {
        postMapper = new PostMapper();
        boardViewService = new BoardViewService(roomAssignmentRepository, commentRepository, postMapper);

        dorm = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS View")
                .build();

        authorWithAssignment = resident("Jan", "Kowalski", "999");
        authorWithoutAssignment = resident("Anna", "Nowak", "105");
        viewer = resident("Ewa", "Wiśniewska", "310");
    }

    @Test
    @DisplayName("Should batch room numbers and comment counts for post feed")
    void toPostDtos_batchesRoomsAndCounts() {
        // Arrange
        Post withRoom = dormPost(authorWithAssignment);
        Post withoutRoom = dormPost(authorWithoutAssignment);
        Room room = Room.builder().roomNumber("201-A").build();

        when(commentRepository.countActiveByPostIds(List.of(withRoom.getId(), withoutRoom.getId())))
                .thenReturn(List.<Object[]>of(new Object[]{withRoom.getId(), 2L}));
        when(roomAssignmentRepository.findActiveByUserIdIn(
                List.of(authorWithAssignment.getId(), authorWithoutAssignment.getId())))
                .thenReturn(List.of(RoomAssignment.builder()
                        .user(authorWithAssignment)
                        .room(room)
                        .isActive(true)
                        .build()));

        // Act
        List<PostDto> result = boardViewService.toPostDtos(List.of(withRoom, withoutRoom), viewer);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).authorRoomNumber()).isEqualTo("201-A");
        assertThat(result.get(0).commentCount()).isEqualTo(2);
        assertThat(result.get(1).authorRoomNumber()).isEqualTo("105");
        assertThat(result.get(1).commentCount()).isZero();
        verify(roomAssignmentRepository, times(1)).findActiveByUserIdIn(any());
        verify(commentRepository, times(1)).countActiveByPostIds(any());
    }

    @Test
    @DisplayName("Should skip comment count query for newly created post")
    void toNewPostDto_skipsCountQuery() {
        // Arrange
        Post post = dormPost(authorWithAssignment);
        Room room = Room.builder().roomNumber("201-A").build();
        when(roomAssignmentRepository.findActiveByUserIdIn(List.of(authorWithAssignment.getId())))
                .thenReturn(List.of(RoomAssignment.builder()
                        .user(authorWithAssignment)
                        .room(room)
                        .isActive(true)
                        .build()));

        // Act
        PostDto result = boardViewService.toNewPostDto(post, viewer);

        // Assert
        assertThat(result.commentCount()).isZero();
        assertThat(result.authorRoomNumber()).isEqualTo("201-A");
        verifyNoInteractions(commentRepository);
    }

    @Test
    @DisplayName("Should query comment count for existing single post")
    void toPostDto_queriesCommentCount() {
        // Arrange
        Post post = dormPost(authorWithoutAssignment);
        when(commentRepository.countActiveByPostId(post.getId())).thenReturn(5L);
        when(roomAssignmentRepository.findActiveByUserIdIn(List.of(authorWithoutAssignment.getId())))
                .thenReturn(List.of());

        // Act
        PostDto result = boardViewService.toPostDto(post, viewer);

        // Assert
        assertThat(result.commentCount()).isEqualTo(5);
        assertThat(result.authorRoomNumber()).isEqualTo("105");
        verify(commentRepository).countActiveByPostId(post.getId());
    }

    @Test
    @DisplayName("Should batch comment author room numbers")
    void toCommentDtos_batchesRooms() {
        // Arrange
        Post post = dormPost(authorWithAssignment);
        Comment comment = Comment.builder()
                .id(UUID.randomUUID())
                .post(post)
                .author(authorWithAssignment)
                .content("Treść komentarza")
                .deleted(false)
                .createdAt(Instant.now())
                .build();
        Room room = Room.builder().roomNumber("201-A").build();
        when(roomAssignmentRepository.findActiveByUserIdIn(List.of(authorWithAssignment.getId())))
                .thenReturn(List.of(RoomAssignment.builder()
                        .user(authorWithAssignment)
                        .room(room)
                        .isActive(true)
                        .build()));

        // Act
        List<CommentDto> result = boardViewService.toCommentDtos(post, List.of(comment), viewer);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).authorRoomNumber()).isEqualTo("201-A");
        assertThat(result.get(0).content()).isEqualTo("Treść komentarza");
    }

    private User resident(String firstName, String lastName, String declaredRoom) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(firstName + "." + lastName + UUID.randomUUID() + "@pk.edu.pl")
                .firstName(firstName)
                .lastName(lastName)
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .declaredRoomNumber(declaredRoom)
                .build();
    }

    private Post dormPost(User author) {
        return Post.builder()
                .id(UUID.randomUUID())
                .author(author)
                .dormitory(dorm)
                .title("Tytuł")
                .content("Treść")
                .category(PostCategory.GENERAL)
                .scope(PostScope.DORMITORY)
                .status(PostStatus.ACTIVE)
                .deleted(false)
                .createdAt(Instant.now())
                .build();
    }
}
