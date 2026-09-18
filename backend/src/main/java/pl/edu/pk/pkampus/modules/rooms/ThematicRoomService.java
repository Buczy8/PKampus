package pl.edu.pk.pkampus.modules.rooms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.rooms.dto.CreateThematicRoomRequestDto;
import pl.edu.pk.pkampus.modules.rooms.dto.ThematicRoomDto;
import pl.edu.pk.pkampus.modules.rooms.dto.UpdateThematicRoomRequestDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThematicRoomService {

    private final ThematicRoomRepository thematicRoomRepository;

    @Transactional(readOnly = true)
    public List<ThematicRoomDto> listForAdmin(User admin) {
        UUID dormitoryId = requireDormAdminDormitoryId(admin);
        return thematicRoomRepository.findAllByDormitoryIdOrderByNameAsc(dormitoryId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ThematicRoomDto create(User admin, CreateThematicRoomRequestDto request) {
        UUID dormitoryId = requireDormAdminDormitoryId(admin);
        Dormitory dormitory = admin.getDormitory();

        LocalTime opening = request.getOpeningTime();
        LocalTime closing = request.getClosingTime();
        boolean spansMidnight = request.getSpansMidnight() != null
                ? request.getSpansMidnight()
                : closing.isBefore(opening);

        validateHours(opening, closing, spansMidnight);

        ThematicRoom room = ThematicRoom.builder()
                .dormitory(dormitory)
                .name(request.getName().trim())
                .maxCapacity(request.getMaxCapacity())
                .openingTime(opening)
                .closingTime(closing)
                .spansMidnight(spansMidnight)
                .maxDurationHours(request.getMaxDurationHours())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .status(request.getStatus() != null ? request.getStatus() : ThematicRoomStatus.AVAILABLE)
                .build();

        ThematicRoom saved = thematicRoomRepository.save(room);
        log.info("ADS {} created thematic room {} in dormitory {}", admin.getEmail(), saved.getName(), dormitoryId);
        return toDto(saved);
    }

    @Transactional
    public ThematicRoomDto update(User admin, UUID roomId, UpdateThematicRoomRequestDto request) {
        UUID dormitoryId = requireDormAdminDormitoryId(admin);
        ThematicRoom room = thematicRoomRepository.findByIdAndDormitoryId(roomId, dormitoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Thematic room not found"));

        if (request.getName() != null && !request.getName().isBlank()) {
            room.setName(request.getName().trim());
        }
        if (request.getMaxCapacity() != null) {
            room.setMaxCapacity(request.getMaxCapacity());
        }
        if (request.getOpeningTime() != null) {
            room.setOpeningTime(request.getOpeningTime());
        }
        if (request.getClosingTime() != null) {
            room.setClosingTime(request.getClosingTime());
        }
        if (request.getSpansMidnight() != null) {
            room.setSpansMidnight(request.getSpansMidnight());
        } else if (request.getOpeningTime() != null || request.getClosingTime() != null) {
            room.setSpansMidnight(room.getClosingTime().isBefore(room.getOpeningTime()));
        }
        if (request.getMaxDurationHours() != null) {
            room.setMaxDurationHours(request.getMaxDurationHours());
        }
        if (request.getDescription() != null) {
            room.setDescription(request.getDescription().isBlank() ? null : request.getDescription().trim());
        }
        if (request.getStatus() != null) {
            room.setStatus(request.getStatus());
        }

        validateHours(room.getOpeningTime(), room.getClosingTime(), room.isSpansMidnight());
        return toDto(thematicRoomRepository.save(room));
    }

    @Transactional(readOnly = true)
    public List<ThematicRoomDto> listAvailableForResident(User user) {
        if (user.getRole() != UserRole.RESIDENT) {
            throw new AccessDeniedException("Only residents can browse the thematic room catalog");
        }
        if (user.getDormitory() == null) {
            throw new BusinessRuleException("Resident is not assigned to a dormitory");
        }
        return thematicRoomRepository
                .findAllByDormitoryIdAndStatusOrderByNameAsc(user.getDormitory().getId(), ThematicRoomStatus.AVAILABLE)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private UUID requireDormAdminDormitoryId(User admin) {
        if (admin.getRole() != UserRole.DORM_ADMIN) {
            throw new AccessDeniedException("Only dormitory administrators can manage thematic rooms");
        }
        if (admin.getDormitory() == null) {
            throw new BusinessRuleException("Administrator account has no dormitory assigned");
        }
        return admin.getDormitory().getId();
    }

    private void validateHours(LocalTime opening, LocalTime closing, boolean spansMidnight) {
        if (opening == null || closing == null) {
            throw new BusinessRuleException("Opening and closing times are required");
        }
        boolean valid = spansMidnight
                ? closing.isBefore(opening)
                : closing.isAfter(opening);
        if (!valid) {
            throw new BusinessRuleException(spansMidnight
                    ? "When spanning midnight, closing time must be before opening time"
                    : "Closing time must be after opening time");
        }
    }

    private ThematicRoomDto toDto(ThematicRoom room) {
        return ThematicRoomDto.builder()
                .id(room.getId())
                .dormitoryId(room.getDormitory().getId())
                .name(room.getName())
                .maxCapacity(room.getMaxCapacity())
                .openingTime(room.getOpeningTime())
                .closingTime(room.getClosingTime())
                .spansMidnight(room.isSpansMidnight())
                .maxDurationHours(room.getMaxDurationHours())
                .description(room.getDescription())
                .status(room.getStatus())
                .createdAt(room.getCreatedAt())
                .build();
    }
}
