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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostMapper unit tests (AAA)")
class PostMapperTest {

    @Mock
    private RoomAssignmentRepository roomAssignmentRepository;

    @Mock
    private CommentRepository commentRepository;

    private PostMapper postMapper;

    private Dormitory dorm;
    private User authorWithAssignment;
    private User authorWithoutAssignment;
    private User viewer;

    @BeforeEach
    void setUp() {
        postMapper = new PostMapper(roomAssignmentRepository, commentRepository);

        dorm = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS Mapper")
                .build();

        authorWithAssignment = resident("Jan", "Kowalski", "999");
        authorWithoutAssignment = resident("Anna", "Nowak", "105");
        viewer = resident("Ewa", "Wiśniewska", "310");
    }

    @Test
    @DisplayName("Should batch room numbers with fallback to declared room")
    void toPostDtosBatchWithFallback() {
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
        List<PostDto> result = postMapper.toPostDtos(List.of(withRoom, withoutRoom), viewer);

        // Assert
        assertEquals(2, result.size());
        assertEquals("201-A", result.get(0).authorRoomNumber());
        assertEquals(2, result.get(0).commentCount());
        assertEquals("105", result.get(1).authorRoomNumber());
        assertEquals(0, result.get(1).commentCount());
        verify(roomAssignmentRepository, times(1)).findActiveByUserIdIn(any());
    }

    @Test
    @DisplayName("Should hide room number for CAMPUS scope without querying assignments")
    void campusScopeHidesRoom() {
        // Arrange
        Post campus = Post.builder()
                .id(UUID.randomUUID())
                .author(authorWithAssignment)
                .dormitory(null)
                .title("Sprzedam rower")
                .content("Treść")
                .category(PostCategory.BUY_SELL)
                .scope(PostScope.CAMPUS)
                .status(PostStatus.ACTIVE)
                .deleted(false)
                .createdAt(Instant.now())
                .build();

        when(commentRepository.countActiveByPostIds(List.of(campus.getId())))
                .thenReturn(List.of());

        // Act
        List<PostDto> result = postMapper.toPostDtos(List.of(campus), viewer);

        // Assert
        assertNull(result.getFirst().authorRoomNumber());
        assertEquals("DS Mapper", result.getFirst().authorDormitoryName());
        verify(roomAssignmentRepository, never()).findActiveByUserIdIn(any());
    }

    @Test
    @DisplayName("Should map single comment with room batch of one")
    void toCommentDtoSingle() {
        // Arrange
        Post post = dormPost(authorWithAssignment);
        Comment comment = Comment.builder()
                .id(UUID.randomUUID())
                .post(post)
                .author(authorWithoutAssignment)
                .content("Mogę pożyczyć")
                .deleted(false)
                .createdAt(Instant.now())
                .build();

        when(roomAssignmentRepository.findActiveByUserIdIn(List.of(authorWithoutAssignment.getId())))
                .thenReturn(List.of());

        // Act
        CommentDto result = postMapper.toCommentDto(comment, post, viewer);

        // Assert
        assertEquals("Mogę pożyczyć", result.content());
        assertEquals("105", result.authorRoomNumber());
        assertFalse(result.mine());
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
