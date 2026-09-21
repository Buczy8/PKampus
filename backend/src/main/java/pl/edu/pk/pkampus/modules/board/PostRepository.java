package pl.edu.pk.pkampus.modules.board;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    @Query("""
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
              AND (
                    :statusFilter = 'ALL'
                    OR (:statusFilter = 'ACTIVE' AND p.status = pl.edu.pk.pkampus.modules.board.PostStatus.ACTIVE)
                    OR (:statusFilter = 'RESOLVED' AND p.status = pl.edu.pk.pkampus.modules.board.PostStatus.RESOLVED)
                  )
            ORDER BY p.createdAt DESC
            """)
    List<Post> findFeed(
            @Param("viewerDormitoryId") UUID viewerDormitoryId,
            @Param("category") PostCategory category,
            @Param("scope") PostScope scope,
            @Param("statusFilter") String statusFilter
    );

    @Query("""
            SELECT p FROM Post p
            JOIN FETCH p.author a
            LEFT JOIN FETCH a.dormitory
            LEFT JOIN FETCH p.dormitory
            WHERE p.id = :id AND p.deleted = FALSE
            """)
    Optional<Post> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("""
            SELECT p FROM Post p
            JOIN FETCH p.author a
            LEFT JOIN FETCH a.dormitory
            LEFT JOIN FETCH p.dormitory
            WHERE p.deleted = FALSE
              AND p.scope = pl.edu.pk.pkampus.modules.board.PostScope.DORMITORY
              AND p.dormitory.id = :dormitoryId
              AND p.status <> pl.edu.pk.pkampus.modules.board.PostStatus.REMOVED_MODERATOR
              AND (:categoryEmpty = true OR p.category = :category)
              AND (
                    :statusFilter = 'ALL'
                    OR (:statusFilter = 'ACTIVE' AND p.status = pl.edu.pk.pkampus.modules.board.PostStatus.ACTIVE)
                    OR (:statusFilter = 'RESOLVED' AND p.status = pl.edu.pk.pkampus.modules.board.PostStatus.RESOLVED)
                  )
            ORDER BY p.createdAt DESC
            """)
    List<Post> findStaffDormitoryFeed(
            @Param("dormitoryId") UUID dormitoryId,
            @Param("categoryEmpty") boolean categoryEmpty,
            @Param("category") PostCategory category,
            @Param("statusFilter") String statusFilter
    );
}
