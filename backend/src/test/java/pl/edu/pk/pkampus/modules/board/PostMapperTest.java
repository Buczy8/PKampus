package pl.edu.pk.pkampus.modules.board;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.edu.pk.pkampus.modules.board.dto.CommentDto;
import pl.edu.pk.pkampus.modules.board.dto.PostDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostMapper pure mapping unit tests (AAA)")
class PostMapperTest {

    private PostMapper postMapper;

    private Dormitory dorm;
    private User author;
    private User viewer;

    @BeforeEach
    void setUp() {
        postMapper = new PostMapper();

        dorm = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS-1 Olimp")
                .build();

        author = User.builder()
                .id(UUID.randomUUID())
                .firstName("Jan")
                .lastName("Kowalski")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .declaredRoomNumber("101")
                .build();

        viewer = User.builder()
                .id(UUID.randomUUID())
                .firstName("Ewa")
                .lastName("Nowak")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .declaredRoomNumber("202")
                .build();
    }

    @Test
    @DisplayName("Should map post to DTO with assigned room and author display name")
    void toDto_withAssignedRoom_usesAssignedRoom() {
        // Arrange
        Post post = Post.builder()
                .id(UUID.randomUUID())
                .author(author)
                .dormitory(dorm)
                .title("Książka")
                .content("Do oddania")
                .category(PostCategory.GENERAL)
                .scope(PostScope.DORMITORY)
                .status(PostStatus.ACTIVE)
                .deleted(false)
                .createdAt(Instant.now())
                .build();

        Map<UUID, String> rooms = Map.of(author.getId(), "315-B");

        // Act
        PostDto dto = postMapper.toDto(post, viewer, 3, rooms);

        // Assert
        assertThat(dto.id()).isEqualTo(post.getId());
        assertThat(dto.title()).isEqualTo("Książka");
        assertThat(dto.authorDisplayName()).isEqualTo("Jan Kowalski");
        assertThat(dto.authorRoomNumber()).isEqualTo("315-B");
        assertThat(dto.authorDormitoryName()).isEqualTo("DS-1 Olimp");
        assertThat(dto.commentCount()).isEqualTo(3);
        assertThat(dto.mine()).isFalse();
    }

    @Test
    @DisplayName("Should fallback to declared room when no assignment present in room map")
    void toDto_withoutAssignedRoom_fallbacksToDeclaredRoom() {
        // Arrange
        Post post = Post.builder()
                .id(UUID.randomUUID())
                .author(author)
                .dormitory(dorm)
                .title("Książka")
                .content("Do oddania")
                .category(PostCategory.GENERAL)
                .scope(PostScope.DORMITORY)
                .status(PostStatus.ACTIVE)
                .deleted(false)
                .createdAt(Instant.now())
                .build();

        // Act
        PostDto dto = postMapper.toDto(post, author, 0, Map.of());

        // Assert
        assertThat(dto.authorRoomNumber()).isEqualTo("101");
        assertThat(dto.mine()).isTrue();
    }

    @Test
    @DisplayName("Should hide room number for CAMPUS scope")
    void toDto_campusScope_hidesRoomNumber() {
        // Arrange
        Post post = Post.builder()
                .id(UUID.randomUUID())
                .author(author)
                .dormitory(null)
                .title("Sprzedam rower")
                .content("Opis")
                .category(PostCategory.BUY_SELL)
                .scope(PostScope.CAMPUS)
                .status(PostStatus.ACTIVE)
                .deleted(false)
                .createdAt(Instant.now())
                .build();

        Map<UUID, String> rooms = Map.of(author.getId(), "315-B");

        // Act
        PostDto dto = postMapper.toDto(post, viewer, 1, rooms);

        // Assert
        assertThat(dto.authorRoomNumber()).isNull();
        assertThat(dto.authorDormitoryName()).isEqualTo("DS-1 Olimp");
    }

    @Test
    @DisplayName("Should map comment to DTO with proper display name and room number")
    void toCommentDto_validComment_mapsProperly() {
        // Arrange
        Post post = Post.builder()
                .id(UUID.randomUUID())
                .author(author)
                .dormitory(dorm)
                .scope(PostScope.DORMITORY)
                .build();

        Comment comment = Comment.builder()
                .id(UUID.randomUUID())
                .post(post)
                .author(author)
                .content("Komentarz testowy")
                .createdAt(Instant.now())
                .build();

        Map<UUID, String> rooms = Map.of(author.getId(), "315-B");

        // Act
        CommentDto dto = postMapper.toCommentDto(comment, post, author, rooms);

        // Assert
        assertThat(dto.id()).isEqualTo(comment.getId());
        assertThat(dto.postId()).isEqualTo(post.getId());
        assertThat(dto.content()).isEqualTo("Komentarz testowy");
        assertThat(dto.authorDisplayName()).isEqualTo("Jan Kowalski");
        assertThat(dto.authorRoomNumber()).isEqualTo("315-B");
        assertThat(dto.mine()).isTrue();
    }
}
