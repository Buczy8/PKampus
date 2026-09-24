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

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public int purgeOldPosts(Instant resolvedCutoff, Instant deletedCutoff) {
        List<UUID> postIds = postRepository.findPostIdsForRetention(resolvedCutoff, deletedCutoff);
        if (postIds.isEmpty()) {
            return 0;
        }

        commentRepository.deleteByPostIdIn(postIds);
        int removed = postRepository.deleteByIdIn(postIds);
        log.info("Purged {} old/resolved/deleted board posts", removed);
        return removed;
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
