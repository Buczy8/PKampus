package pl.edu.pk.pkampus.modules.board;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.util.UUID;

/**
 * Shared preconditions for board endpoints: authorization guards,
 * feed filter parsing and pagination validation.
 */
@Component
@RequiredArgsConstructor
class PostAccessPolicy {

    public static final int MAX_FEED_PAGE_SIZE = 50;

    private final PostRepository postRepository;

    public Dormitory requireActiveResident(User user) {
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

    public UUID requireStaffDormitoryId(User staff) {
        if (staff.getRole() != UserRole.RECEPTIONIST && staff.getRole() != UserRole.DORM_ADMIN) {
            throw new AccessDeniedException("Only receptionist or dormitory admin can moderate the board");
        }
        if (staff.getDormitory() == null) {
            throw new BusinessRuleException("Staff account has no dormitory assigned");
        }
        return staff.getDormitory().getId();
    }

    public Post requireOwnVisiblePost(User user, UUID postId) {
        Post post = requireVisiblePost(user, postId);
        if (!post.getAuthor().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the author can modify this post");
        }
        return post;
    }

    public Post requireVisiblePost(User user, UUID postId) {
        Post post = postRepository.findByIdAndNotDeleted(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (post.getStatus() == PostStatus.REMOVED_MODERATOR) {
            throw new ResourceNotFoundException("Post not found");
        }
        if (post.getScope() == PostScope.DORMITORY) {
            Dormitory viewerDorm = user.getDormitory();
            if (viewerDorm == null || viewerDorm.getId() == null
                    || post.getDormitory() == null
                    || !post.getDormitory().getId().equals(viewerDorm.getId())) {
                throw new ResourceNotFoundException("Post not found");
            }
        }
        return post;
    }

    public Post requireStaffModeratablePost(UUID postId, UUID dormitoryId) {
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

    public PostStatus resolvePostStatus(String statusFilter, PostStatusFilter defaultIfBlank) {
        return PostStatusFilter.from(statusFilter, defaultIfBlank).toPostStatus();
    }

    public PageRequest feedPageable(int page, int size) {
        if (page < 0) {
            throw new BusinessRuleException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_FEED_PAGE_SIZE) {
            throw new BusinessRuleException("size must be between 1 and " + MAX_FEED_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public PageRequest commentPageable(int page, int size) {
        if (page < 0) {
            throw new BusinessRuleException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_FEED_PAGE_SIZE) {
            throw new BusinessRuleException("size must be between 1 and " + MAX_FEED_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
    }
}
