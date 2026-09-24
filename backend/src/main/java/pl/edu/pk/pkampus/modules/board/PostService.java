package pl.edu.pk.pkampus.modules.board;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.PagedResponse;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.board.dto.CommentDto;
import pl.edu.pk.pkampus.modules.board.dto.CreateCommentRequestDto;
import pl.edu.pk.pkampus.modules.board.dto.CreatePostRequestDto;
import pl.edu.pk.pkampus.modules.board.dto.PostDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private static final int MAX_FEED_PAGE_SIZE = 50;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;

    @Transactional(readOnly = true)
    public PagedResponse<PostDto> listFeed(
            User user,
            PostCategory category,
            PostScope scope,
            String statusFilter,
            int page,
            int size
    ) {
        Dormitory dorm = requireActiveResident(user);
        String filter = normalizeStatusFilter(statusFilter);
        Page<Post> posts = postRepository.findFeed(
                dorm.getId(), category, scope, filter, feedPageable(page, size));
        Map<UUID, Integer> counts = commentCounts(posts.stream().map(Post::getId).toList());
        Map<UUID, String> rooms = roomNumbersByUserIds(dormitoryAuthorIds(posts.getContent()));
        List<PostDto> content = posts.stream()
                .map(post -> toDto(post, user, counts.getOrDefault(post.getId(), 0), rooms))
                .toList();
        return PagedResponse.of(content, posts.getNumber(), posts.getSize(), posts.getTotalElements());
    }

    @Transactional
    public PostDto create(User user, CreatePostRequestDto request) {
        Dormitory dorm = requireActiveResident(user);

        String title = request.title().trim();
        String content = request.content().trim();
        if (title.isEmpty() || content.isEmpty()) {
            throw new BusinessRuleException("Title and content are required");
        }

        Dormitory postDorm = request.scope() == PostScope.DORMITORY ? dorm : null;

        Post post = Post.builder()
                .author(user)
                .dormitory(postDorm)
                .title(title)
                .content(content)
                .category(request.category())
                .scope(request.scope())
                .status(PostStatus.ACTIVE)
                .deleted(false)
                .build();

        Post saved = postRepository.saveAndFlush(post);
        log.info("Resident {} created board post {} ({}/{})",
                user.getEmail(), saved.getId(), saved.getCategory(), saved.getScope());
        return toDto(saved, user, 0, roomNumbersByUserIds(dormitoryAuthorIds(List.of(saved))));
    }

    @Transactional
    public PostDto resolve(User user, UUID postId) {
        requireActiveResident(user);
        Post post = requireOwnVisiblePost(user, postId);
        if (post.getStatus() != PostStatus.ACTIVE) {
            throw new BusinessRuleException("Only ACTIVE posts can be marked as resolved");
        }
        post.setStatus(PostStatus.RESOLVED);
        Post saved = postRepository.save(post);
        int count = (int) commentRepository.countActiveByPostId(saved.getId());
        return toDto(saved, user, count, roomNumbersByUserIds(dormitoryAuthorIds(List.of(saved))));
    }

    @Transactional
    public void softDelete(User user, UUID postId) {
        requireActiveResident(user);
        Post post = requireOwnVisiblePost(user, postId);
        post.setDeleted(true);
        post.setDeletedAt(Instant.now());
        postRepository.save(post);
        log.info("Resident {} soft-deleted board post {}", user.getEmail(), postId);
    }

    @Transactional(readOnly = true)
    public List<CommentDto> listComments(User user, UUID postId) {
        requireActiveResident(user);
        Post post = requireVisiblePost(user, postId);
        List<Comment> comments = commentRepository.findActiveByPostIdOrderByCreatedAtAsc(post.getId());
        Map<UUID, String> rooms = roomNumbersByUserIds(commentAuthorIds(post, comments));
        return comments.stream()
                .map(c -> toCommentDto(c, post, user, rooms))
                .toList();
    }

    @Transactional
    public CommentDto addComment(User user, UUID postId, CreateCommentRequestDto request) {
        requireActiveResident(user);
        Post post = requireVisiblePost(user, postId);
        if (post.getStatus() == PostStatus.REMOVED_MODERATOR) {
            throw new BusinessRuleException("Cannot comment on a removed post");
        }

        String content = request.content().trim();
        if (content.isEmpty()) {
            throw new BusinessRuleException("Comment content is required");
        }

        Comment comment = Comment.builder()
                .post(post)
                .author(user)
                .content(content)
                .deleted(false)
                .build();
        Comment saved = commentRepository.saveAndFlush(comment);
        log.info("Resident {} commented on post {}", user.getEmail(), postId);
        return toCommentDto(saved, post, user, roomNumbersByUserIds(commentAuthorIds(post, List.of(saved))));
    }

    @Transactional(readOnly = true)
    public PagedResponse<PostDto> listForStaff(
            User staff,
            PostCategory category,
            String statusFilter,
            int page,
            int size
    ) {
        UUID dormitoryId = requireStaffDormitoryId(staff);
        String filter = normalizeStatusFilter(statusFilter == null ? "ALL" : statusFilter);
        Page<Post> posts = postRepository.findStaffDormitoryFeed(
                dormitoryId,
                category == null,
                category != null ? category : PostCategory.GENERAL,
                filter,
                feedPageable(page, size)
        );
        Map<UUID, Integer> counts = commentCounts(posts.stream().map(Post::getId).toList());
        Map<UUID, String> rooms = roomNumbersByUserIds(dormitoryAuthorIds(posts.getContent()));
        List<PostDto> content = posts.stream()
                .map(post -> toDto(post, staff, counts.getOrDefault(post.getId(), 0), rooms))
                .toList();
        return PagedResponse.of(content, posts.getNumber(), posts.getSize(), posts.getTotalElements());
    }

    @Transactional
    public PostDto removePostAsModerator(User staff, UUID postId) {
        UUID dormitoryId = requireStaffDormitoryId(staff);
        Post post = requireStaffModeratablePost(postId, dormitoryId);
        post.setStatus(PostStatus.REMOVED_MODERATOR);
        Post saved = postRepository.save(post);
        log.info("Staff {} moderated board post {} to REMOVED_MODERATOR", staff.getEmail(), postId);
        int count = (int) commentRepository.countActiveByPostId(saved.getId());
        return toDto(saved, staff, count, roomNumbersByUserIds(dormitoryAuthorIds(List.of(saved))));
    }

    @Transactional(readOnly = true)
    public List<CommentDto> listCommentsForStaff(User staff, UUID postId) {
        UUID dormitoryId = requireStaffDormitoryId(staff);
        Post post = requireStaffModeratablePost(postId, dormitoryId);
        List<Comment> comments = commentRepository.findActiveByPostIdOrderByCreatedAtAsc(post.getId());
        Map<UUID, String> rooms = roomNumbersByUserIds(commentAuthorIds(post, comments));
        return comments.stream()
                .map(c -> toCommentDto(c, post, staff, rooms))
                .toList();
    }

    @Transactional
    public void removeCommentAsModerator(User staff, UUID commentId) {
        UUID dormitoryId = requireStaffDormitoryId(staff);
        Comment comment = commentRepository.findByIdWithPost(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (comment.isDeleted()) {
            throw new ResourceNotFoundException("Comment not found");
        }
        Post post = comment.getPost();
        requireStaffModeratablePost(post.getId(), dormitoryId);
        comment.setDeleted(true);
        comment.setDeletedAt(Instant.now());
        commentRepository.save(comment);
        log.info("Staff {} soft-deleted comment {} on post {}",
                staff.getEmail(), commentId, post.getId());
    }

    private Post requireStaffModeratablePost(UUID postId, UUID dormitoryId) {
        Post post = postRepository.findByIdAndNotDeleted(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (post.getStatus() == PostStatus.REMOVED_MODERATOR) {
            throw new ResourceNotFoundException("Post not found");
        }
        if (post.getScope() != PostScope.DORMITORY
                || post.getDormitory() == null
                || !post.getDormitory().getId().equals(dormitoryId)) {
            throw new ResourceNotFoundException("Post not found");
        }
        return post;
    }

    private UUID requireStaffDormitoryId(User staff) {
        if (staff.getRole() != UserRole.RECEPTIONIST && staff.getRole() != UserRole.DORM_ADMIN) {
            throw new AccessDeniedException("Only receptionist or dormitory admin can moderate the board");
        }
        if (staff.getDormitory() == null) {
            throw new BusinessRuleException("Staff account has no dormitory assigned");
        }
        return staff.getDormitory().getId();
    }

    private Post requireOwnVisiblePost(User user, UUID postId) {
        Post post = requireVisiblePost(user, postId);
        if (!post.getAuthor().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the author can modify this post");
        }
        return post;
    }

    private Post requireVisiblePost(User user, UUID postId) {
        Post post = postRepository.findByIdAndNotDeleted(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (post.getStatus() == PostStatus.REMOVED_MODERATOR) {
            throw new ResourceNotFoundException("Post not found");
        }
        if (post.getScope() == PostScope.DORMITORY) {
            UUID viewerDorm = user.getDormitory().getId();
            if (post.getDormitory() == null || !post.getDormitory().getId().equals(viewerDorm)) {
                throw new ResourceNotFoundException("Post not found");
            }
        }
        return post;
    }

    private Dormitory requireActiveResident(User user) {
        if (user.getRole() != UserRole.RESIDENT) {
            throw new AccountStatusException("Only residents can use the community board");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountStatusException("Account must be ACTIVE to use the community board");
        }
        if (user.getDormitory() == null) {
            throw new AccountStatusException("Resident is not assigned to a dormitory");
        }
        return user.getDormitory();
    }

    private String normalizeStatusFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return "ACTIVE";
        }
        String normalized = statusFilter.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ACTIVE", "RESOLVED", "ALL" -> normalized;
            default -> throw new BusinessRuleException("status must be ACTIVE, RESOLVED, or ALL");
        };
    }

    private PageRequest feedPageable(int page, int size) {
        if (page < 0) {
            throw new BusinessRuleException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_FEED_PAGE_SIZE) {
            throw new BusinessRuleException("size must be between 1 and " + MAX_FEED_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
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
                post.getCreatedAt().atZone(WARSAW).toOffsetDateTime()
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
                comment.getCreatedAt().atZone(WARSAW).toOffsetDateTime()
        );
    }
}
