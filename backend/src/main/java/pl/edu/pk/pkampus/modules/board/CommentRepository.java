package pl.edu.pk.pkampus.modules.board;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @Query("""
            SELECT c FROM Comment c
            JOIN FETCH c.author a
            LEFT JOIN FETCH a.dormitory
            WHERE c.post.id = :postId AND c.deleted = FALSE
            ORDER BY c.createdAt ASC
            """)
    List<Comment> findActiveByPostIdOrderByCreatedAtAsc(@Param("postId") UUID postId);

    @Query("""
            SELECT c.post.id, COUNT(c) FROM Comment c
            WHERE c.deleted = FALSE AND c.post.id IN :postIds
            GROUP BY c.post.id
            """)
    List<Object[]> countActiveByPostIds(@Param("postIds") Collection<UUID> postIds);

    @Query("""
            SELECT COUNT(c) FROM Comment c
            WHERE c.post.id = :postId AND c.deleted = FALSE
            """)
    long countActiveByPostId(@Param("postId") UUID postId);

    @Query("""
            SELECT c FROM Comment c
            JOIN FETCH c.author a
            LEFT JOIN FETCH a.dormitory
            JOIN FETCH c.post p
            LEFT JOIN FETCH p.dormitory
            WHERE c.id = :id
            """)
    java.util.Optional<Comment> findByIdWithPost(@Param("id") UUID id);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Comment c WHERE c.deleted = TRUE AND c.deletedAt < :cutoff")
    int deleteOldSoftDeletedComments(@Param("cutoff") java.time.Instant cutoff);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Comment c WHERE c.post.id IN :postIds")
    int deleteByPostIdIn(@Param("postIds") java.util.Collection<UUID> postIds);
}

