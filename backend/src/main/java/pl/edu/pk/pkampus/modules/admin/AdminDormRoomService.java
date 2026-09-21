package pl.edu.pk.pkampus.modules.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.admin.dto.CreateDormRoomRequestDto;
import pl.edu.pk.pkampus.modules.admin.dto.DormRoomDto;
import pl.edu.pk.pkampus.modules.admin.dto.UpdateDormRoomRequestDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDormRoomService {

    private final RoomRepository roomRepository;

    @Transactional(readOnly = true)
    public List<DormRoomDto> list(User admin) {
        UUID dormitoryId = requireDormAdminDormitoryId(admin);
        return roomRepository.findAllByDormitoryIdOrderByFloorAscRoomNumberAsc(dormitoryId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public DormRoomDto create(User admin, CreateDormRoomRequestDto request) {
        UUID dormitoryId = requireDormAdminDormitoryId(admin);
        Dormitory dormitory = admin.getDormitory();

        String roomNumber = request.getRoomNumber().trim();
        if (roomRepository.existsByDormitoryIdAndRoomNumber(dormitoryId, roomNumber)) {
            throw new BusinessRuleException("A room with this number already exists in the dormitory");
        }

        if (request.getFloor() > dormitory.getFloorsCount()) {
            throw new BusinessRuleException(
                    "Floor exceeds dormitory floors count (" + dormitory.getFloorsCount() + ")");
        }

        Room room = Room.builder()
                .dormitory(dormitory)
                .roomNumber(roomNumber)
                .floor(request.getFloor())
                .capacity(request.getCapacity())
                .build();

        Room saved = roomRepository.save(room);
        log.info("ADS {} created room {} in dormitory {}",
                admin.getEmail(), saved.getRoomNumber(), dormitoryId);
        return toDto(saved);
    }

    @Transactional
    public DormRoomDto update(User admin, UUID id, UpdateDormRoomRequestDto request) {
        UUID dormitoryId = requireDormAdminDormitoryId(admin);
        Room room = roomRepository.findByIdAndDormitoryId(id, dormitoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        Dormitory dormitory = admin.getDormitory();

        if (request.getRoomNumber() != null && !request.getRoomNumber().isBlank()) {
            String roomNumber = request.getRoomNumber().trim();
            if (roomRepository.existsByDormitoryIdAndRoomNumberAndIdNot(dormitoryId, roomNumber, room.getId())) {
                throw new BusinessRuleException("A room with this number already exists in the dormitory");
            }
            room.setRoomNumber(roomNumber);
        }
        if (request.getFloor() != null) {
            if (request.getFloor() > dormitory.getFloorsCount()) {
                throw new BusinessRuleException(
                        "Floor exceeds dormitory floors count (" + dormitory.getFloorsCount() + ")");
            }
            room.setFloor(request.getFloor());
        }
        if (request.getCapacity() != null) {
            room.setCapacity(request.getCapacity());
        }

        return toDto(roomRepository.save(room));
    }

    private UUID requireDormAdminDormitoryId(User admin) {
        if (admin.getRole() != UserRole.DORM_ADMIN) {
            throw new AccessDeniedException("Only dormitory administrators can manage rooms");
        }
        if (admin.getDormitory() == null) {
            throw new BusinessRuleException("Administrator account has no dormitory assigned");
        }
        return admin.getDormitory().getId();
    }

    private DormRoomDto toDto(Room room) {
        return DormRoomDto.builder()
                .id(room.getId())
                .dormitoryId(room.getDormitory().getId())
                .roomNumber(room.getRoomNumber())
                .floor(room.getFloor())
                .capacity(room.getCapacity())
                .createdAt(room.getCreatedAt())
                .build();
    }
}
