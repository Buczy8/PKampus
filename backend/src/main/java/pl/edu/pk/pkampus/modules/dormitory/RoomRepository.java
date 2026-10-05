package pl.edu.pk.pkampus.modules.dormitory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByDormitoryIdAndRoomNumber(UUID dormitoryId, String roomNumber);

    Optional<Room> findByIdAndDormitoryId(UUID id, UUID dormitoryId);

    List<Room> findAllByDormitoryIdOrderByFloorAscRoomNumberAsc(UUID dormitoryId);

    boolean existsByDormitoryIdAndRoomNumber(UUID dormitoryId, String roomNumber);

    boolean existsByDormitoryIdAndRoomNumberAndIdNot(UUID dormitoryId, String roomNumber, UUID id);
}
