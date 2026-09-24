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
import pl.edu.pk.pkampus.modules.user.User;

import java.util.List;
import java.util.UUID;

/**
 * Staff side of the community board: moderation of dormitory-scoped posts and comments.
 * Resident operations live in {@link PostService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostModerationService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostMapper postMapper;
    private final PostAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public PagedResponse<PostDto> listForStaff(
            User staff,
            PostCategory category,
            String statusFilter,
            int page,
            int size
    ) {
        UUID dormitoryId = accessPolicy.requireStaffDormitoryId(staff);
        PostStatus status = accessPolicy.resolvePostStatus(statusFilter, PostStatusFilter.ALL);
        Page<Post> posts = postRepository.findStaffDormitoryFeed(
                dormitoryId,
                category,
                status,
                accessPolicy.feedPageable(page, size)
        );
        List<PostDto> content = postMapper.toPostDtos(posts.getContent(), staff);
        return PagedResponse.of(content, posts.getNumber(), posts.getSize(), posts.getTotalElements());
    }

    @Transactional
    public PostDto removePostAsModerator(User staff, UUID postId) {
        UUID dormitoryId = accessPolicy.requireStaffDormitoryId(staff);
        Post post = accessPolicy.requireStaffModeratablePost(postId, dormitoryId);
        post.removeAsModerator();
        Post saved = postRepository.save(post);
        log.info("Staff {} moderated board post {} to REMOVED_MODERATOR", staff.getId(), postId);
        return postMapper.toPostDto(saved, staff);
    }

    @Transactional(readOnly = true)
    public List<CommentDto> listCommentsForStaff(User staff, UUID postId) {
        UUID dormitoryId = accessPolicy.requireStaffDormitoryId(staff);
        Post post = accessPolicy.requireStaffModeratablePost(postId, dormitoryId);
        List<Comment> comments = commentRepository.findActiveByPostIdOrderByCreatedAtAsc(post.getId());
        return postMapper.toCommentDtos(post, comments, staff);
    }

    @Transactional
    public void removeCommentAsModerator(User staff, UUID commentId) {
        UUID dormitoryId = accessPolicy.requireStaffDormitoryId(staff);
        Comment comment = commentRepository.findByIdWithPost(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (comment.isDeleted()) {
            throw new ResourceNotFoundException("Comment not found");
        }
        Post post = comment.getPost();
        accessPolicy.requireStaffModeratablePost(post.getId(), dormitoryId);
        comment.softDelete();
        commentRepository.save(comment);
        log.info("Staff {} soft-deleted comment {} on post {}",
                staff.getId(), commentId, post.getId());
    }
}
