package pl.edu.pk.pkampus.modules.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        UUID dormitoryId = AdminScope.requireDormitoryId(admin, "receptionists");
        return userRepository
                .findAllByDormitoryIdAndRoleOrderByLastNameAscFirstNameAsc(dormitoryId, UserRole.RECEPTIONIST)
                .stream()
                .map(ReceptionistDto::from)
                .toList();
    }

    @Transactional
    public ReceptionistDto create(User admin, CreateReceptionistRequestDto request) {
        Dormitory dormitory = AdminScope.requireDormitory(admin, "receptionists");

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
                admin.getEmail(), saved.getEmail(), dormitory.getId());
        return ReceptionistDto.from(saved);
    }

    @Transactional
    public ReceptionistDto update(User admin, UUID id, UpdateReceptionistRequestDto request) {
        UUID dormitoryId = AdminScope.requireDormitoryId(admin, "receptionists");
        User receptionist = userRepository
                .findByIdAndDormitoryIdAndRole(id, dormitoryId, UserRole.RECEPTIONIST)
                .orElseThrow(() -> new ResourceNotFoundException("Receptionist not found"));

        receptionist.updatePersonalData(
                request.getFirstName(),
                request.getLastName(),
                request.getPhoneNumber()
        );

        if (request.getStatus() != null) {
            applyStatus(receptionist, request.getStatus());
        }

        return ReceptionistDto.from(userRepository.save(receptionist));
    }

    private void applyStatus(User receptionist, UserStatus status) {
        if (!PATCH_STATUSES.contains(status)) {
            throw new BusinessRuleException("Status must be ACTIVE or BLOCKED");
        }
        receptionist.setStatus(status);
        if (status == UserStatus.BLOCKED) {
            tokenRevocationService.revokeUser(receptionist.getId());
        } else {
            tokenRevocationService.clearRevocation(receptionist.getId());
        }
    }
}
