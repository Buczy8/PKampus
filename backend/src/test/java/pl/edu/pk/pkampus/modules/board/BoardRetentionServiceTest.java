package pl.edu.pk.pkampus.modules.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BoardRetentionService unit tests (AAA)")
class BoardRetentionServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private BoardRetentionService boardRetentionService;

    @Test
    @DisplayName("Should return 0 and not delete when no post IDs match retention criteria")
    void purgeOldPosts_noPostsMatch_returnsZero() {
        // Arrange
        Instant resolvedCutoff = Instant.now().minusSeconds(3600);
        Instant deletedCutoff = Instant.now().minusSeconds(1800);
        when(postRepository.findPostIdsForRetention(eq(resolvedCutoff), eq(deletedCutoff), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of());

        // Act
        int result = boardRetentionService.purgeOldPosts(resolvedCutoff, deletedCutoff);

        // Assert
        assertThat(result).isZero();
        verify(commentRepository, never()).deleteByPostIdIn(any());
        verify(postRepository, never()).deleteByIdIn(any());
    }

    @Test
    @DisplayName("Should purge comments and posts when IDs match retention criteria")
    void purgeOldPosts_postsFound_purgesCommentsThenPosts() {
        // Arrange
        Instant resolvedCutoff = Instant.now().minusSeconds(3600);
        Instant deletedCutoff = Instant.now().minusSeconds(1800);
        List<UUID> postIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(postRepository.findPostIdsForRetention(eq(resolvedCutoff), eq(deletedCutoff), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(postIds)
                .thenReturn(List.of());
        when(postRepository.deleteByIdIn(postIds)).thenReturn(2);

        // Act
        int result = boardRetentionService.purgeOldPosts(resolvedCutoff, deletedCutoff);

        // Assert
        assertThat(result).isEqualTo(2);
        verify(commentRepository).deleteByPostIdIn(postIds);
        verify(postRepository).deleteByIdIn(postIds);
    }

    @Test
    @DisplayName("Should delegate comment purging to repository")
    void purgeOldComments_delegatesToRepository() {
        // Arrange
        Instant cutoff = Instant.now().minusSeconds(3600);
        when(commentRepository.deleteOldSoftDeletedComments(cutoff)).thenReturn(7);

        // Act
        int result = boardRetentionService.purgeOldComments(cutoff);

        // Assert
        assertThat(result).isEqualTo(7);
        verify(commentRepository).deleteOldSoftDeletedComments(cutoff);
    }
}
