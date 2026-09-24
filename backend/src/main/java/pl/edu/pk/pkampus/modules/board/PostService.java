package pl.edu.pk.pkampus.modules.board;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.PagedResponse;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.board.dto.CommentDto;
import pl.edu.pk.pkampus.modules.board.dto.CreateCommentRequestDto;
import pl.edu.pk.pkampus.modules.board.dto.CreatePostRequestDto;
import pl.edu.pk.pkampus.modules.board.dto.PostDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.List;
import java.util.UUID;

/**
 * Resident side of the community board: publishing, resolving and commenting.
 * Staff moderation lives in {@link PostModerationService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final BoardViewService boardViewService;
    private final PostAccessPolicy accessPolicy;
    private final BoardRateLimiterService rateLimiterService;

    @Transactional(readOnly = true)
    public PagedResponse<PostDto> listFeed(
            User user,
            PostCategory category,
            PostScope scope,
            String statusFilter,
            int page,
            int size
    ) {
        User current = accessPolicy.requireActiveResidentUser(user);
        Dormitory dorm = current.getDormitory();
        PostStatus status = PostStatusFilter.from(statusFilter, PostStatusFilter.ACTIVE).toPostStatus();
        Page<Post> posts = postRepository.findFeed(
                dorm.getId(), category, scope, status, BoardPagination.feedPageable(page, size));
        List<PostDto> content = boardViewService.toPostDtos(posts.getContent(), current);
        return PagedResponse.of(content, posts.getNumber(), posts.getSize(), posts.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PostDto getById(User user, UUID postId) {
        User current = accessPolicy.requireActiveResidentUser(user);
        Post post = accessPolicy.requireVisiblePost(current, postId);
        return boardViewService.toPostDto(post, current);
    }

    @Transactional
    public PostDto create(User user, CreatePostRequestDto request) {
        User current = accessPolicy.requireActiveResidentUser(user);
        Dormitory dorm = current.getDormitory();

        String title = request.title().trim();
        String content = request.content().trim();
        if (title.isEmpty() || content.isEmpty()) {
            throw new BusinessRuleException("Title and content are required");
        }
        rejectHtml(title, "Title");
        rejectHtml(content, "Content");

        rateLimiterService.checkPostRateLimit(current.getId());

        Dormitory postDorm = request.scope() == PostScope.DORMITORY ? dorm : null;

        Post post = Post.builder()
                .author(current)
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
                current.getId(), saved.getId(), saved.getCategory(), saved.getScope());
        return boardViewService.toNewPostDto(saved, current);
    }

    @Transactional
    public PostDto resolve(User user, UUID postId) {
        User current = accessPolicy.requireActiveResidentUser(user);
        Post post = accessPolicy.requireOwnVisiblePost(current, postId);
        post.markResolved();
        Post saved = postRepository.save(post);
        return boardViewService.toPostDto(saved, current);
    }

    @Transactional
    public void softDelete(User user, UUID postId) {
        User current = accessPolicy.requireActiveResidentUser(user);
        Post post = accessPolicy.requireOwnVisiblePost(current, postId);
        post.softDelete();
        postRepository.save(post);
        log.info("Resident {} soft-deleted board post {}", current.getId(), postId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<CommentDto> listComments(User user, UUID postId, int page, int size) {
        User current = accessPolicy.requireActiveResidentUser(user);
        Post post = accessPolicy.requireVisiblePost(current, postId);
        Page<Comment> comments = commentRepository.findActiveByPostId(
                post.getId(), BoardPagination.commentPageable(page, size));
        List<CommentDto> content = boardViewService.toCommentDtos(post, comments.getContent(), current);
        return PagedResponse.of(content, comments.getNumber(), comments.getSize(), comments.getTotalElements());
    }

    @Transactional
    public CommentDto addComment(User user, UUID postId, CreateCommentRequestDto request) {
        User current = accessPolicy.requireActiveResidentUser(user);
        Post post = accessPolicy.requireVisiblePost(current, postId);
        if (post.getStatus() == PostStatus.REMOVED_MODERATOR) {
            throw new BusinessRuleException("Cannot comment on a removed post");
        }

        String content = request.content().trim();
        if (content.isEmpty()) {
            throw new BusinessRuleException("Comment content is required");
        }
        rejectHtml(content, "Comment content");

        rateLimiterService.checkCommentRateLimit(current.getId());

        Comment comment = Comment.builder()
                .post(post)
                .author(current)
                .content(content)
                .deleted(false)
                .build();
        Comment saved = commentRepository.saveAndFlush(comment);
        log.info("Resident {} commented on post {}", current.getId(), postId);
        return boardViewService.toCommentDto(saved, post, current);
    }

    @Transactional
    public void deleteComment(User user, UUID commentId) {
        User current = accessPolicy.requireActiveResidentUser(user);
        Comment comment = commentRepository.findByIdWithPost(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (comment.isDeleted()) {
            throw new ResourceNotFoundException("Comment not found");
        }
        accessPolicy.requireVisiblePost(current, comment.getPost());
        if (!comment.getAuthor().getId().equals(current.getId())) {
            throw new AccessDeniedException("Only the author can delete this comment");
        }
        comment.softDelete();
        commentRepository.save(comment);
        log.info("Resident {} soft-deleted comment {} on post {}", current.getId(), commentId, comment.getPost().getId());
    }

    private static final java.util.regex.Pattern HTML_TAG =
            java.util.regex.Pattern.compile("<\\s*/?\\s*[a-zA-Z][^>]*>");

    /**
     * Defense-in-depth against stored XSS. React escapes by default, but the API
     * must not persist HTML tags — one {@code dangerouslySetInnerHTML} on the
     * frontend would otherwise turn stored content into an XSS payload.
     */
    static void rejectHtml(String value, String fieldLabel) {
        if (value != null && HTML_TAG.matcher(value).find()) {
            throw new BusinessRuleException(fieldLabel + " must not contain HTML");
        }
    }
}
