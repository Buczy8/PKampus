package pl.edu.pk.pkampus.modules.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Post and Comment domain behavior unit tests (AAA)")
class PostDomainTest {

    @Test
    @DisplayName("Repeated softDelete keeps the original deletion timestamp")
    void softDeleteIsIdempotent() {
        // Arrange
        Post post = activePost();

        // Act
        post.softDelete();
        Instant firstDeletedAt = post.getDeletedAt();
        post.softDelete();

        // Assert
        assertTrue(post.isDeleted());
        assertEquals(firstDeletedAt, post.getDeletedAt());
    }

    @Test
    @DisplayName("Repeated removeAsModerator throws instead of silently re-setting status")
    void removeAsModeratorGuardsRepeatedRemoval() {
        // Arrange
        Post post = activePost();

        // Act
        post.removeAsModerator();

        // Assert
        assertEquals(PostStatus.REMOVED_MODERATOR, post.getStatus());
        assertThrows(BusinessRuleException.class, post::removeAsModerator);
    }

    @Test
    @DisplayName("markResolved on non-ACTIVE post throws")
    void markResolvedGuardsStatus() {
        // Arrange
        Post resolved = activePost().toBuilder().status(PostStatus.RESOLVED).build();

        // Act & Assert
        assertThrows(BusinessRuleException.class, resolved::markResolved);
    }

    @Test
    @DisplayName("Repeated comment softDelete keeps the original deletion timestamp")
    void commentSoftDeleteIsIdempotent() {
        // Arrange
        Comment comment = Comment.builder()
                .id(UUID.randomUUID())
                .content("Treść")
                .deleted(false)
                .createdAt(Instant.now())
                .build();

        // Act
        comment.softDelete();
        Instant firstDeletedAt = comment.getDeletedAt();
        comment.softDelete();

        // Assert
        assertTrue(comment.isDeleted());
        assertEquals(firstDeletedAt, comment.getDeletedAt());
    }

    private Post activePost() {
        return Post.builder()
                .id(UUID.randomUUID())
                .title("Tytuł")
                .content("Treść")
                .category(PostCategory.GENERAL)
                .scope(PostScope.DORMITORY)
                .status(PostStatus.ACTIVE)
                .deleted(false)
                .createdAt(Instant.now())
                .build();
    }
}
