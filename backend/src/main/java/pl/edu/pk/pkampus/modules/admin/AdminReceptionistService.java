package pl.edu.pk.pkampus.modules.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.admin.dto.CreateReceptionistRequestDto;
import pl.edu.pk.pkampus.modules.admin.dto.ReceptionistDto;
import pl.edu.pk.pkampus.modules.admin.dto.UpdateReceptionistRequestDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.TokenRevocationService;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReceptionistService {

    private static final Set<UserStatus> PATCH_STATUSES = EnumSet.of(UserStatus.ACTIVE, UserStatus.BLOCKED);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationService tokenRevocationService;

    @Transactional(readOnly = true)
    public List<ReceptionistDto> list(User admin) {
        UUID dormitoryId = requireDormAdminDormitoryId(admin);
        return userRepository
                .findAllByDormitoryIdAndRoleOrderByLastNameAscFirstNameAsc(dormitoryId, UserRole.RECEPTIONIST)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ReceptionistDto create(User admin, CreateReceptionistRequestDto request) {
        UUID dormitoryId = requireDormAdminDormitoryId(admin);
        Dormitory dormitory = admin.getDormitory();

        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email is already registered");
        }

        User receptionist = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phoneNumber(request.getPhoneNumber().trim())
                .role(UserRole.RECEPTIONIST)
                .status(UserStatus.MUST_CHANGE_PASSWORD)
                .dormitory(dormitory)
                .build();

        User saved = userRepository.save(receptionist);
        log.info("ADS {} created RECEPTIONIST {} for dormitory {}",
                admin.getEmail(), saved.getEmail(), dormitoryId);
        return toDto(saved);
    }

    @Transactional
    public ReceptionistDto update(User admin, UUID id, UpdateReceptionistRequestDto request) {
        UUID dormitoryId = requireDormAdminDormitoryId(admin);
        User receptionist = userRepository
                .findByIdAndDormitoryIdAndRole(id, dormitoryId, UserRole.RECEPTIONIST)
                .orElseThrow(() -> new ResourceNotFoundException("Receptionist not found"));

        receptionist.updatePersonalData(
                request.getFirstName(),
                request.getLastName(),
                request.getPhoneNumber()
        );

        if (request.getStatus() != null) {
            if (!PATCH_STATUSES.contains(request.getStatus())) {
                throw new BusinessRuleException("Status must be ACTIVE or BLOCKED");
            }
            receptionist.setStatus(request.getStatus());
            if (request.getStatus() == UserStatus.BLOCKED) {
                tokenRevocationService.revokeUser(receptionist.getId());
            } else {
                tokenRevocationService.clearRevocation(receptionist.getId());
            }
        }

        return toDto(userRepository.save(receptionist));
    }

    private UUID requireDormAdminDormitoryId(User admin) {
        if (admin.getRole() != UserRole.DORM_ADMIN) {
            throw new AccessDeniedException("Only dormitory administrators can manage receptionists");
        }
        if (admin.getDormitory() == null) {
            throw new BusinessRuleException("Administrator account has no dormitory assigned");
        }
        return admin.getDormitory().getId();
    }

    private ReceptionistDto toDto(User user) {
        Dormitory dorm = user.getDormitory();
        return ReceptionistDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .status(user.getStatus())
                .dormitoryId(dorm != null ? dorm.getId() : null)
                .dormitoryName(dorm != null ? dorm.getName() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
