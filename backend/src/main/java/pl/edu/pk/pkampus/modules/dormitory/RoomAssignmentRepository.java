package pl.edu.pk.pkampus.modules.dormitory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomAssignmentRepository extends JpaRepository<RoomAssignment, UUID> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"room", "user"})
    Optional<RoomAssignment> findByUserIdAndIsActiveTrue(UUID userId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"room", "user"})
    @Query("SELECT a FROM RoomAssignment a WHERE a.user.id IN :userIds AND a.isActive = true")
    List<RoomAssignment> findActiveByUserIdIn(@Param("userIds") Collection<UUID> userIds);

    List<RoomAssignment> findAllByUserId(UUID userId);

    List<RoomAssignment> findAllByRoomIdAndIsActiveTrue(UUID roomId);
}
