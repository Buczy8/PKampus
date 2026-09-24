package pl.edu.pk.pkampus.modules.board;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 * Aggregates view-related data (comment counts, assigned room numbers)
 * and coordinates PostMapper to assemble DTOs.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class BoardViewService {

    private final RoomAssignmentRepository roomAssignmentRepository;
    private final CommentRepository commentRepository;
    private final PostMapper postMapper;

    public List<PostDto> toPostDtos(List<Post> posts, User viewer) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Map<UUID, Integer> counts = commentCounts(posts.stream().map(Post::getId).toList());
        Map<UUID, String> rooms = roomNumbersByUserIds(dormitoryAuthorIds(posts));
        return posts.stream()
                .map(post -> postMapper.toDto(post, viewer, counts.getOrDefault(post.getId(), 0), rooms))
                .toList();
    }

    public PostDto toPostDto(Post post, User viewer) {
        int count = (int) commentRepository.countActiveByPostId(post.getId());
        Map<UUID, String> rooms = roomNumbersByUserIds(dormitoryAuthorIds(List.of(post)));
        return postMapper.toDto(post, viewer, count, rooms);
    }

    public PostDto toNewPostDto(Post post, User viewer) {
        // Newly created post has 0 comments by definition — avoids a redundant count query
        Map<UUID, String> rooms = roomNumbersByUserIds(dormitoryAuthorIds(List.of(post)));
        return postMapper.toDto(post, viewer, 0, rooms);
    }

    public List<CommentDto> toCommentDtos(Post post, List<Comment> comments, User viewer) {
        if (comments.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> rooms = roomNumbersByUserIds(commentAuthorIds(post, comments));
        return comments.stream()
                .map(comment -> postMapper.toCommentDto(comment, post, viewer, rooms))
                .toList();
    }

    public CommentDto toCommentDto(Comment comment, Post post, User viewer) {
        Map<UUID, String> rooms = roomNumbersByUserIds(commentAuthorIds(post, List.of(comment)));
        return postMapper.toCommentDto(comment, post, viewer, rooms);
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
}
