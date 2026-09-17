package pl.edu.pk.pkampus.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.edu.pk.pkampus.model.RoomAssignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomAssignmentRepository extends JpaRepository<RoomAssignment, UUID> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"room"})
    Optional<RoomAssignment> findByUserIdAndIsActiveTrue(UUID userId);

    List<RoomAssignment> findAllByUserId(UUID userId);

    List<RoomAssignment> findAllByRoomIdAndIsActiveTrue(UUID roomId);
}
