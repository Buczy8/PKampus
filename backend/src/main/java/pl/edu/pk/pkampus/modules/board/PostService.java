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
    private final PostMapper postMapper;
    private final PostAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public PagedResponse<PostDto> listFeed(
            User user,
            PostCategory category,
            PostScope scope,
            String statusFilter,
            int page,
            int size
    ) {
        Dormitory dorm = accessPolicy.requireActiveResident(user);
        PostStatus status = accessPolicy.resolvePostStatus(statusFilter, PostStatusFilter.ACTIVE);
        Page<Post> posts = postRepository.findFeed(
                dorm.getId(), category, scope, status, accessPolicy.feedPageable(page, size));
        List<PostDto> content = postMapper.toPostDtos(posts.getContent(), user);
        return PagedResponse.of(content, posts.getNumber(), posts.getSize(), posts.getTotalElements());
    }

    @Transactional
    public PostDto create(User user, CreatePostRequestDto request) {
        Dormitory dorm = accessPolicy.requireActiveResident(user);

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
                user.getId(), saved.getId(), saved.getCategory(), saved.getScope());
        return postMapper.toPostDto(saved, user);
    }

    @Transactional
    public PostDto resolve(User user, UUID postId) {
        accessPolicy.requireActiveResident(user);
        Post post = accessPolicy.requireOwnVisiblePost(user, postId);
        post.markResolved();
        Post saved = postRepository.save(post);
        return postMapper.toPostDto(saved, user);
    }

    @Transactional
    public void softDelete(User user, UUID postId) {
        accessPolicy.requireActiveResident(user);
        Post post = accessPolicy.requireOwnVisiblePost(user, postId);
        post.softDelete();
        postRepository.save(post);
        log.info("Resident {} soft-deleted board post {}", user.getId(), postId);
    }

    @Transactional(readOnly = true)
    public List<CommentDto> listComments(User user, UUID postId) {
        accessPolicy.requireActiveResident(user);
        Post post = accessPolicy.requireVisiblePost(user, postId);
        List<Comment> comments = commentRepository.findActiveByPostIdOrderByCreatedAtAsc(post.getId());
        return postMapper.toCommentDtos(post, comments, user);
    }

    @Transactional
    public CommentDto addComment(User user, UUID postId, CreateCommentRequestDto request) {
        accessPolicy.requireActiveResident(user);
        Post post = accessPolicy.requireVisiblePost(user, postId);
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
        log.info("Resident {} commented on post {}", user.getId(), postId);
        return postMapper.toCommentDto(saved, post, user);
    }

    @Transactional
    public void deleteComment(User user, UUID commentId) {
        accessPolicy.requireActiveResident(user);
        Comment comment = commentRepository.findByIdWithPost(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (comment.isDeleted()) {
            throw new ResourceNotFoundException("Comment not found");
        }
        accessPolicy.requireVisiblePost(user, comment.getPost().getId());
        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the author can delete this comment");
        }
        comment.softDelete();
        commentRepository.save(comment);
        log.info("Resident {} soft-deleted comment {} on post {}", user.getId(), commentId, comment.getPost().getId());
    }
}
