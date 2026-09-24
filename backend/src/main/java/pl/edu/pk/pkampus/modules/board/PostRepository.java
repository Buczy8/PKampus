package pl.edu.pk.pkampus.modules.board;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    @Query(
            value = """
                    SELECT p FROM Post p
                    JOIN FETCH p.author a
                    LEFT JOIN FETCH a.dormitory
                    LEFT JOIN FETCH p.dormitory
                    WHERE p.deleted = FALSE
                      AND p.status <> pl.edu.pk.pkampus.modules.board.PostStatus.REMOVED_MODERATOR
                      AND (p.scope = pl.edu.pk.pkampus.modules.board.PostScope.CAMPUS
                           OR p.dormitory.id = :viewerDormitoryId)
                      AND (:category IS NULL OR p.category = :category)
                      AND (:scope IS NULL OR p.scope = :scope)
                      AND (:status IS NULL OR p.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Post p
                    WHERE p.deleted = FALSE
                      AND p.status <> pl.edu.pk.pkampus.modules.board.PostStatus.REMOVED_MODERATOR
                      AND (p.scope = pl.edu.pk.pkampus.modules.board.PostScope.CAMPUS
                           OR p.dormitory.id = :viewerDormitoryId)
                      AND (:category IS NULL OR p.category = :category)
                      AND (:scope IS NULL OR p.scope = :scope)
                      AND (:status IS NULL OR p.status = :status)
                    """)
    Page<Post> findFeed(
            @Param("viewerDormitoryId") UUID viewerDormitoryId,
            @Param("category") PostCategory category,
            @Param("scope") PostScope scope,
            @Param("status") PostStatus status,
            Pageable pageable
    );

    @Query("""
            SELECT p FROM Post p
            JOIN FETCH p.author a
            LEFT JOIN FETCH a.dormitory
            LEFT JOIN FETCH p.dormitory
            WHERE p.id = :id AND p.deleted = FALSE
            """)
    Optional<Post> findByIdAndNotDeleted(@Param("id") UUID id);

    /**
     * Moderation feed for staff. Covers both scopes so CAMPUS posts are visible
     * to moderators (FR-BOARD-06: CAMPUS is moderated by ADS of all dormitories
     * and by the Superadmin).
     *
     * <p>RECEPTIONIST uses {@link #findDormitoryModerationFeed} instead, because
     * CAMPUS posts are not moderatable by receptionists (see
     * {@code PostAccessPolicy.requireModeratablePost}) — listing them would
     * expose posts the caller cannot act on.
     *
     * @param dormitoryId dormitory to scope DORMITORY posts to, or {@code null}
     *                    for the global Superadmin view (all dormitories)
     */
    @Query(
            value = """
                    SELECT p FROM Post p
                    JOIN FETCH p.author a
                    LEFT JOIN FETCH a.dormitory
                    LEFT JOIN FETCH p.dormitory
                    WHERE p.deleted = FALSE
                      AND p.status <> pl.edu.pk.pkampus.modules.board.PostStatus.REMOVED_MODERATOR
                      AND (:dormitoryId IS NULL
                           OR p.scope = pl.edu.pk.pkampus.modules.board.PostScope.CAMPUS
                           OR p.dormitory.id = :dormitoryId)
                      AND (:category IS NULL OR p.category = :category)
                      AND (:status IS NULL OR p.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Post p
                    WHERE p.deleted = FALSE
                      AND p.status <> pl.edu.pk.pkampus.modules.board.PostStatus.REMOVED_MODERATOR
                      AND (:dormitoryId IS NULL
                           OR p.scope = pl.edu.pk.pkampus.modules.board.PostScope.CAMPUS
                           OR p.dormitory.id = :dormitoryId)
                      AND (:category IS NULL OR p.category = :category)
                      AND (:status IS NULL OR p.status = :status)
                    """)
    Page<Post> findModerationFeed(
            @Param("dormitoryId") UUID dormitoryId,
            @Param("category") PostCategory category,
            @Param("status") PostStatus status,
            Pageable pageable
    );

    /**
     * Dormitory-only moderation feed for receptionists. RECEPTIONIST can moderate
     * solely DORMITORY posts of their own dormitory; CAMPUS posts return 404 for
     * them, so they must not appear in the listing either.
     */
    @Query(
            value = """
                    SELECT p FROM Post p
                    JOIN FETCH p.author a
                    LEFT JOIN FETCH a.dormitory
                    LEFT JOIN FETCH p.dormitory
                    WHERE p.deleted = FALSE
                      AND p.status <> pl.edu.pk.pkampus.modules.board.PostStatus.REMOVED_MODERATOR
                      AND p.scope = pl.edu.pk.pkampus.modules.board.PostScope.DORMITORY
                      AND p.dormitory.id = :dormitoryId
                      AND (:category IS NULL OR p.category = :category)
                      AND (:status IS NULL OR p.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Post p
                    WHERE p.deleted = FALSE
                      AND p.status <> pl.edu.pk.pkampus.modules.board.PostStatus.REMOVED_MODERATOR
                      AND p.scope = pl.edu.pk.pkampus.modules.board.PostScope.DORMITORY
                      AND p.dormitory.id = :dormitoryId
                      AND (:category IS NULL OR p.category = :category)
                      AND (:status IS NULL OR p.status = :status)
                    """)
    Page<Post> findDormitoryModerationFeed(
            @Param("dormitoryId") UUID dormitoryId,
            @Param("category") PostCategory category,
            @Param("status") PostStatus status,
            Pageable pageable
    );

    @Query("""
            SELECT p.id FROM Post p
            WHERE (p.status = pl.edu.pk.pkampus.modules.board.PostStatus.RESOLVED AND p.updatedAt < :resolvedCutoff)
               OR (p.deleted = TRUE AND p.deletedAt < :deletedCutoff)
               OR (p.status = pl.edu.pk.pkampus.modules.board.PostStatus.REMOVED_MODERATOR AND p.updatedAt < :deletedCutoff)
            """)
    List<UUID> findPostIdsForRetention(
            @Param("resolvedCutoff") Instant resolvedCutoff,
            @Param("deletedCutoff") Instant deletedCutoff
    );

    @Query("""
            SELECT p.id FROM Post p
            WHERE (p.status = pl.edu.pk.pkampus.modules.board.PostStatus.RESOLVED AND p.updatedAt < :resolvedCutoff)
               OR (p.deleted = TRUE AND p.deletedAt < :deletedCutoff)
               OR (p.status = pl.edu.pk.pkampus.modules.board.PostStatus.REMOVED_MODERATOR AND p.updatedAt < :deletedCutoff)
            """)
    List<UUID> findPostIdsForRetention(
            @Param("resolvedCutoff") Instant resolvedCutoff,
            @Param("deletedCutoff") Instant deletedCutoff,
            org.springframework.data.domain.Pageable pageable
    );

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM Post p WHERE p.id IN :ids")
    int deleteByIdIn(@Param("ids") Collection<UUID> ids);
}

