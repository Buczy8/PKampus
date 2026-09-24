package pl.edu.pk.pkampus.modules.board;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.user.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dormitory_id")
    private Dormitory dormitory;

    @Column(name = "title", length = 150, nullable = false)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private PostCategory category;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 20, nullable = false)
    private PostScope scope = PostScope.DORMITORY;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PostStatus status = PostStatus.ACTIVE;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Marks the post as resolved. Only the author can trigger this via
     * {@code PostService.resolve}; only ACTIVE posts can transition.
     */
    public void markResolved() {
        if (this.status != PostStatus.ACTIVE) {
            throw new BusinessRuleException("Only ACTIVE posts can be marked as resolved");
        }
        this.status = PostStatus.RESOLVED;
    }

    /**
     * Soft-deletes the post. Only the author can trigger this via
     * {@code PostService.softDelete}. Repeating the call is a no-op and keeps
     * the original deletion timestamp.
     */
    public void softDelete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        this.deletedAt = Instant.now();
    }

    /**
     * Hides the post from residents. Only staff can trigger this via
     * {@code PostModerationService.removePostAsModerator} after the
     * dormitory/scope checks in {@code PostAccessPolicy}.
     */
    public void removeAsModerator() {
        if (this.status == PostStatus.REMOVED_MODERATOR) {
            throw new BusinessRuleException("Post is already removed by moderator");
        }
        this.status = PostStatus.REMOVED_MODERATOR;
    }
}
