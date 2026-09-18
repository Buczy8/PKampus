package pl.edu.pk.pkampus.modules.laundry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.laundry.dto.AdminLaundryMachineDto;
import pl.edu.pk.pkampus.modules.laundry.dto.CreateLaundryMachineRequestDto;
import pl.edu.pk.pkampus.modules.laundry.dto.UpdateLaundryMachineRequestDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLaundryMachineService {

    private final LaundryMachineRepository laundryMachineRepository;

    @Transactional(readOnly = true)
    public List<AdminLaundryMachineDto> listForAdmin(User admin) {
        UUID dormitoryId = requireDormAdminDormitoryId(admin);
        return laundryMachineRepository.findAllByDormitoryIdOrderByMachineIdentifierAsc(dormitoryId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public AdminLaundryMachineDto create(User admin, CreateLaundryMachineRequestDto request) {
        UUID dormitoryId = requireDormAdminDormitoryId(admin);
        Dormitory dormitory = admin.getDormitory();

        String identifier = request.getMachineIdentifier().trim();
        String floor = request.getFloorLocation().trim();
        if (laundryMachineRepository.existsByDormitoryIdAndMachineIdentifierIgnoreCase(dormitoryId, identifier)) {
            throw new BusinessRuleException("A laundry machine with this identifier already exists in the dormitory");
        }

        LaundryMachine machine = LaundryMachine.builder()
                .dormitory(dormitory)
                .machineIdentifier(identifier)
                .floorLocation(floor)
                .status(LaundryMachineStatus.AVAILABLE)
                .build();

        LaundryMachine saved = laundryMachineRepository.save(machine);
        log.info("ADS {} created laundry machine {} in dormitory {}",
                admin.getEmail(), saved.getMachineIdentifier(), dormitoryId);
        return toDto(saved);
    }

    @Transactional
    public AdminLaundryMachineDto update(User admin, UUID id, UpdateLaundryMachineRequestDto request) {
        UUID dormitoryId = requireDormAdminDormitoryId(admin);
        LaundryMachine machine = laundryMachineRepository.findByIdAndDormitoryId(id, dormitoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Laundry machine not found"));

        if (request.getMachineIdentifier() != null && !request.getMachineIdentifier().isBlank()) {
            String identifier = request.getMachineIdentifier().trim();
            if (laundryMachineRepository.existsByDormitoryIdAndMachineIdentifierIgnoreCaseAndIdNot(
                    dormitoryId, identifier, machine.getId())) {
                throw new BusinessRuleException("A laundry machine with this identifier already exists in the dormitory");
            }
            machine.setMachineIdentifier(identifier);
        }
        if (request.getFloorLocation() != null && !request.getFloorLocation().isBlank()) {
            machine.setFloorLocation(request.getFloorLocation().trim());
        }
        if (request.getStatus() != null) {
            machine.setStatus(request.getStatus());
        }

        return toDto(laundryMachineRepository.save(machine));
    }

    private UUID requireDormAdminDormitoryId(User admin) {
        if (admin.getRole() != UserRole.DORM_ADMIN) {
            throw new AccessDeniedException("Only dormitory administrators can manage laundry machines");
        }
        if (admin.getDormitory() == null) {
            throw new BusinessRuleException("Administrator account has no dormitory assigned");
        }
        return admin.getDormitory().getId();
    }

    private AdminLaundryMachineDto toDto(LaundryMachine machine) {
        return AdminLaundryMachineDto.builder()
                .id(machine.getId())
                .dormitoryId(machine.getDormitory().getId())
                .machineIdentifier(machine.getMachineIdentifier())
                .floorLocation(machine.getFloorLocation())
                .status(machine.getStatus())
                .createdAt(machine.getCreatedAt())
                .build();
    }
}
