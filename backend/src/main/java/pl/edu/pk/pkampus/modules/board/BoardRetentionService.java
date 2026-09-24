package pl.edu.pk.pkampus.modules.board;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public domain service exposing community board data retention and cleanup operations.
 * Encapsulates PostRepository and CommentRepository within the board module.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardRetentionService {

    private static final int PURGE_BATCH_SIZE = 500;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public int purgeOldPosts(Instant resolvedCutoff, Instant deletedCutoff) {
        int totalRemoved = 0;
        List<UUID> batch;
        do {
            batch = postRepository.findPostIdsForRetention(
                    resolvedCutoff,
                    deletedCutoff,
                    org.springframework.data.domain.PageRequest.of(0, PURGE_BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            commentRepository.deleteByPostIdIn(batch);
            totalRemoved += postRepository.deleteByIdIn(batch);
        } while (batch.size() >= PURGE_BATCH_SIZE);
        if (totalRemoved > 0) {
            log.info("Purged {} old/resolved/deleted board posts", totalRemoved);
        }
        return totalRemoved;
    }

    @Transactional
    public int purgeOldComments(Instant cutoff) {
        int removed = commentRepository.deleteOldSoftDeletedComments(cutoff);
        if (removed > 0) {
            log.info("Purged {} standalone soft-deleted comments", removed);
        }
        return removed;
    }
}
