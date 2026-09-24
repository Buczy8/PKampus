package pl.edu.pk.pkampus.modules.board;

import org.springframework.stereotype.Component;
import pl.edu.pk.pkampus.common.config.TimeConfig;
import pl.edu.pk.pkampus.modules.board.dto.CommentDto;
import pl.edu.pk.pkampus.modules.board.dto.PostDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.Map;
import java.util.UUID;

/**
 * Pure mapper transforming domain entities and resolved view data into DTOs.
 * Free of I/O, database dependencies, or repository calls.
 */
@Component
class PostMapper {

    public PostDto toDto(Post post, User viewer, int commentCount, Map<UUID, String> roomsByUserId) {
        User author = post.getAuthor();
        String displayName = author.getFirstName() + " " + author.getLastName();
        String dormName = author.getDormitory() != null
                ? author.getDormitory().getName()
                : (post.getDormitory() != null ? post.getDormitory().getName() : null);

        String roomNumber = resolveRoomNumber(author, post.getScope(), roomsByUserId);

        return new PostDto(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getCategory(),
                post.getScope(),
                post.getStatus(),
                displayName,
                roomNumber,
                dormName,
                author.getId().equals(viewer.getId()),
                commentCount,
                post.getCreatedAt().atZone(TimeConfig.WARSAW).toOffsetDateTime()
        );
    }

    public CommentDto toCommentDto(Comment comment, Post post, User viewer, Map<UUID, String> roomsByUserId) {
        User author = comment.getAuthor();
        String displayName = author.getFirstName() + " " + author.getLastName();
        String dormName = author.getDormitory() != null ? author.getDormitory().getName() : null;

        String roomNumber = resolveRoomNumber(author, post.getScope(), roomsByUserId);

        return new CommentDto(
                comment.getId(),
                post.getId(),
                comment.getContent(),
                displayName,
                roomNumber,
                dormName,
                author.getId().equals(viewer.getId()),
                comment.getCreatedAt().atZone(TimeConfig.WARSAW).toOffsetDateTime()
        );
    }

    private String resolveRoomNumber(User author, PostScope scope, Map<UUID, String> roomsByUserId) {
        if (scope != PostScope.DORMITORY) {
            return null;
        }
        if (roomsByUserId == null) {
            return author.getDeclaredRoomNumber();
        }
        String assigned = roomsByUserId.get(author.getId());
        return assigned != null ? assigned : author.getDeclaredRoomNumber();
    }
}
