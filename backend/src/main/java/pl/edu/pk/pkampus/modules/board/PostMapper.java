package pl.edu.pk.pkampus.modules.board;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.edu.pk.pkampus.common.config.TimeConfig;
import pl.edu.pk.pkampus.modules.board.dto.CommentDto;
import pl.edu.pk.pkampus.modules.board.dto.PostDto;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Assembles board DTOs from entities. Owns all view-related data fetching
 * (comment counts, room numbers) so services stay focused on use cases.
 */
@Component
@RequiredArgsConstructor
class PostMapper {

    private final RoomAssignmentRepository roomAssignmentRepository;
    private final CommentRepository commentRepository;

    public List<PostDto> toPostDtos(List<Post> posts, User viewer) {
        Map<UUID, Integer> counts = commentCounts(posts.stream().map(Post::getId).toList());
        Map<UUID, String> rooms = roomNumbersByUserIds(dormitoryAuthorIds(posts));
        return posts.stream()
                .map(post -> toDto(post, viewer, counts.getOrDefault(post.getId(), 0), rooms))
                .toList();
    }

    public PostDto toPostDto(Post post, User viewer) {
        int count = (int) commentRepository.countActiveByPostId(post.getId());
        Map<UUID, String> rooms = roomNumbersByUserIds(dormitoryAuthorIds(List.of(post)));
        return toDto(post, viewer, count, rooms);
    }

    public List<CommentDto> toCommentDtos(Post post, List<Comment> comments, User viewer) {
        Map<UUID, String> rooms = roomNumbersByUserIds(commentAuthorIds(post, comments));
        return comments.stream()
                .map(comment -> toCommentDto(comment, post, viewer, rooms))
                .toList();
    }

    public CommentDto toCommentDto(Comment comment, Post post, User viewer) {
        Map<UUID, String> rooms = roomNumbersByUserIds(commentAuthorIds(post, List.of(comment)));
        return toCommentDto(comment, post, viewer, rooms);
    }

    private Map<UUID, Integer> commentCounts(List<UUID> postIds) {
        Map<UUID, Integer> map = new HashMap<>();
        if (postIds.isEmpty()) {
            return map;
        }
        for (Object[] row : commentRepository.countActiveByPostIds(postIds)) {
            map.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return map;
    }

    private Map<UUID, String> roomNumbersByUserIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinctIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return roomAssignmentRepository.findActiveByUserIdIn(distinctIds).stream()
                .filter(ra -> ra.getUser() != null && ra.getUser().getId() != null && ra.getRoom() != null)
                .collect(Collectors.toMap(
                        ra -> ra.getUser().getId(),
                        ra -> ra.getRoom().getRoomNumber(),
                        (first, second) -> first));
    }

    private List<UUID> dormitoryAuthorIds(List<Post> posts) {
        return posts.stream()
                .filter(post -> post.getScope() == PostScope.DORMITORY && post.getAuthor() != null)
                .map(post -> post.getAuthor().getId())
                .distinct()
                .toList();
    }

    private List<UUID> commentAuthorIds(Post post, List<Comment> comments) {
        if (post.getScope() != PostScope.DORMITORY) {
            return List.of();
        }
        return comments.stream()
                .filter(comment -> comment.getAuthor() != null)
                .map(comment -> comment.getAuthor().getId())
                .distinct()
                .toList();
    }

    private String resolveRoomNumber(User author, PostScope scope, Map<UUID, String> roomsByUserId) {
        if (scope != PostScope.DORMITORY) {
            return null;
        }
        String assigned = roomsByUserId.get(author.getId());
        return assigned != null ? assigned : author.getDeclaredRoomNumber();
    }

    private PostDto toDto(Post post, User viewer, int commentCount, Map<UUID, String> roomsByUserId) {
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

    private CommentDto toCommentDto(Comment comment, Post post, User viewer, Map<UUID, String> roomsByUserId) {
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
}
