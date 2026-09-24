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

    @Query(
            value = """
                    SELECT p FROM Post p
                    JOIN FETCH p.author a
                    LEFT JOIN FETCH a.dormitory
                    LEFT JOIN FETCH p.dormitory
                    WHERE p.deleted = FALSE
                      AND p.scope = pl.edu.pk.pkampus.modules.board.PostScope.DORMITORY
                      AND p.dormitory.id = :dormitoryId
                      AND p.status <> pl.edu.pk.pkampus.modules.board.PostStatus.REMOVED_MODERATOR
                      AND (:category IS NULL OR p.category = :category)
                      AND (:status IS NULL OR p.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Post p
                    WHERE p.deleted = FALSE
                      AND p.scope = pl.edu.pk.pkampus.modules.board.PostScope.DORMITORY
                      AND p.dormitory.id = :dormitoryId
                      AND p.status <> pl.edu.pk.pkampus.modules.board.PostStatus.REMOVED_MODERATOR
                      AND (:category IS NULL OR p.category = :category)
                      AND (:status IS NULL OR p.status = :status)
                    """)
    Page<Post> findStaffDormitoryFeed(
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

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Post p WHERE p.id IN :ids")
    int deleteByIdIn(@Param("ids") Collection<UUID> ids);
}

