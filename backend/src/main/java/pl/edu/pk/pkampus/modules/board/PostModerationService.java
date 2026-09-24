package pl.edu.pk.pkampus.modules.board;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.PagedResponse;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.board.dto.CommentDto;
import pl.edu.pk.pkampus.modules.board.dto.PostDto;
import pl.edu.pk.pkampus.modules.board.PostAccessPolicy.ModeratorScope;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;

import java.util.List;
import java.util.UUID;

/**
 * Staff side of the community board: moderation of dormitory- and campus-scoped posts and comments.
 * Resident operations live in {@link PostService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostModerationService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final BoardViewService boardViewService;
    private final PostAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public PagedResponse<PostDto> listForStaff(
            User staff,
            PostCategory category,
            String statusFilter,
            int page,
            int size
    ) {
        ModeratorScope scope = accessPolicy.requireModeratorScope(staff);
        PostStatus status = PostStatusFilter.from(statusFilter, PostStatusFilter.ALL).toPostStatus();
        Page<Post> posts;
        if (scope.role() == UserRole.RECEPTIONIST) {
            posts = postRepository.findDormitoryModerationFeed(
                    scope.dormitoryId(),
                    category,
                    status,
                    BoardPagination.feedPageable(page, size)
            );
        } else {
            posts = postRepository.findModerationFeed(
                    scope.global() ? null : scope.dormitoryId(),
                    category,
                    status,
                    BoardPagination.feedPageable(page, size)
            );
        }
        List<PostDto> content = boardViewService.toPostDtos(posts.getContent(), staff);
        return PagedResponse.of(content, posts.getNumber(), posts.getSize(), posts.getTotalElements());
    }

    @Transactional
    public PostDto removePostAsModerator(User staff, UUID postId) {
        ModeratorScope scope = accessPolicy.requireModeratorScope(staff);
        Post post = accessPolicy.requireModeratablePost(postId, scope);
        post.removeAsModerator();
        Post saved = postRepository.save(post);
        log.info("Staff {} moderated board post {} to REMOVED_MODERATOR", staff.getId(), postId);
        return boardViewService.toPostDto(saved, staff);
    }

    @Transactional(readOnly = true)
    public PagedResponse<CommentDto> listCommentsForStaff(User staff, UUID postId, int page, int size) {
        ModeratorScope scope = accessPolicy.requireModeratorScope(staff);
        Post post = accessPolicy.requireModeratablePost(postId, scope);
        Page<Comment> comments = commentRepository.findActiveByPostId(
                post.getId(), BoardPagination.commentPageable(page, size));
        List<CommentDto> content = boardViewService.toCommentDtos(post, comments.getContent(), staff);
        return PagedResponse.of(content, comments.getNumber(), comments.getSize(), comments.getTotalElements());
    }

    @Transactional
    public void removeCommentAsModerator(User staff, UUID commentId) {
        ModeratorScope scope = accessPolicy.requireModeratorScope(staff);
        Comment comment = commentRepository.findByIdWithPost(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (comment.isDeleted()) {
            throw new ResourceNotFoundException("Comment not found");
        }
        Post post = comment.getPost();
        accessPolicy.requireModeratablePost(post, scope);
        comment.softDelete();
        commentRepository.save(comment);
        log.info("Staff {} soft-deleted comment {} on post {}",
                staff.getId(), commentId, post.getId());
    }
}
