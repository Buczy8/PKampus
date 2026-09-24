package pl.edu.pk.pkampus.modules.board;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.util.UUID;

/**
 * Authorization and access guards for community board posts and comments.
 *
 * <p>Callers pass the {@code @AuthenticationPrincipal} object, which is a detached
 * JPA entity held in the {@code SecurityContext} (see {@code JwtAuthenticationFilter}
 * + {@code AuthenticatedUserCache}). This policy never trusts its mutable fields
 * except the identifier — role, status and dormitory are always reloaded from the
 * database so blocking / reassignment takes effect immediately and no lazy proxy
 * is touched outside a transaction.
 */
@Component
@RequiredArgsConstructor
class PostAccessPolicy {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /**
     * Reloads the principal to a managed entity and enforces ACTIVE resident rules.
     * Use the returned instance for associations (e.g. post author) and visibility checks.
     */
    public User requireActiveResidentUser(User principal) {
        User fresh = reload(principal);
        if (fresh.getRole() != UserRole.RESIDENT) {
            throw new AccountStatusException("Only residents can use the community board");
        }
        if (fresh.getStatus() != UserStatus.ACTIVE) {
            throw new AccountStatusException("Account must be ACTIVE to use the community board");
        }
        if (fresh.getDormitory() == null) {
            throw new AccountStatusException("Resident is not assigned to a dormitory");
        }
        return fresh;
    }

    public Dormitory requireActiveResident(User principal) {
        return requireActiveResidentUser(principal).getDormitory();
    }

    public UUID requireStaffDormitoryId(User principal) {
        User staff = reload(principal);
        if (staff.getRole() != UserRole.RECEPTIONIST && staff.getRole() != UserRole.DORM_ADMIN) {
            throw new AccessDeniedException("Only receptionist or dormitory admin can moderate the board");
        }
        if (staff.getDormitory() == null) {
            throw new BusinessRuleException("Staff account has no dormitory assigned");
        }
        return staff.getDormitory().getId();
    }

    public Post requireOwnVisiblePost(User freshUser, UUID postId) {
        Post post = requireVisiblePost(freshUser, postId);
        if (!post.getAuthor().getId().equals(freshUser.getId())) {
            throw new AccessDeniedException("Only the author can modify this post");
        }
        return post;
    }

    /**
     * @param freshUser user already resolved via {@link #requireActiveResidentUser(User)};
     *                  its dormitory is used for scope checks, never the detached principal.
     */
    public Post requireVisiblePost(User freshUser, UUID postId) {
        Post post = postRepository.findByIdAndNotDeleted(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (post.getStatus() == PostStatus.REMOVED_MODERATOR) {
            throw new ResourceNotFoundException("Post not found");
        }
        if (post.getScope() == PostScope.DORMITORY) {
            Dormitory viewerDorm = freshUser.getDormitory();
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

    private User reload(User principal) {
        if (principal == null || principal.getId() == null) {
            throw new AccountStatusException("Authentication required");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}

