package pl.edu.pk.pkampus.modules.admin.dormrooms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.admin.AdminResource;
import pl.edu.pk.pkampus.modules.admin.AdminScope;
import pl.edu.pk.pkampus.modules.admin.dto.CreateDormRoomRequestDto;
import pl.edu.pk.pkampus.modules.admin.dto.DormRoomDto;
import pl.edu.pk.pkampus.modules.admin.dto.UpdateDormRoomRequestDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomRepository;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDormRoomService {

    private final RoomRepository roomRepository;

    @Transactional(readOnly = true)
    public List<DormRoomDto> list(User admin) {
        UUID dormitoryId = AdminScope.requireDormitoryId(admin, AdminResource.ROOMS);
        return roomRepository.findAllByDormitoryIdOrderByFloorAscRoomNumberAsc(dormitoryId).stream()
                .map(DormRoomDto::from)
                .toList();
    }

    @Transactional
    public DormRoomDto create(User admin, CreateDormRoomRequestDto request) {
        Dormitory dormitory = AdminScope.requireDormitory(admin, AdminResource.ROOMS);

        String roomNumber = request.getRoomNumber().trim();
        assertRoomNumberFree(dormitory.getId(), roomNumber, null);
        assertFloorWithinDormitory(dormitory, request.getFloor());

        Room room = Room.builder()
                .dormitory(dormitory)
                .roomNumber(roomNumber)
                .floor(request.getFloor())
                .capacity(request.getCapacity())
                .build();

        Room saved = roomRepository.save(room);
        log.info("ADS {} created room {} in dormitory {}",
                admin.getEmail(), saved.getRoomNumber(), dormitory.getId());
        return DormRoomDto.from(saved);
    }

    @Transactional
    public DormRoomDto update(User admin, UUID id, UpdateDormRoomRequestDto request) {
        UUID dormitoryId = AdminScope.requireDormitoryId(admin, AdminResource.ROOMS);
        Room room = roomRepository.findByIdAndDormitoryId(id, dormitoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        Dormitory dormitory = admin.getDormitory();

        if (request.getRoomNumber() != null && !request.getRoomNumber().isBlank()) {
            assertRoomNumberFree(dormitoryId, request.getRoomNumber().trim(), room.getId());
        }
        if (request.getFloor() != null) {
            assertFloorWithinDormitory(dormitory, request.getFloor());
        }
        room.applyPatch(request.getRoomNumber(), request.getFloor(), request.getCapacity());

        return DormRoomDto.from(roomRepository.save(room));
    }

    private void assertRoomNumberFree(UUID dormitoryId, String roomNumber, UUID ignoreId) {
        boolean exists = ignoreId == null
                ? roomRepository.existsByDormitoryIdAndRoomNumber(dormitoryId, roomNumber)
                : roomRepository.existsByDormitoryIdAndRoomNumberAndIdNot(dormitoryId, roomNumber, ignoreId);
        if (exists) {
            throw new BusinessRuleException("A room with this number already exists in the dormitory");
        }
    }

    private static void assertFloorWithinDormitory(Dormitory dormitory, Integer floor) {
        if (floor > dormitory.getFloorsCount()) {
            throw new BusinessRuleException(
                    "Floor exceeds dormitory floors count (" + dormitory.getFloorsCount() + ")");
        }
    }
}
