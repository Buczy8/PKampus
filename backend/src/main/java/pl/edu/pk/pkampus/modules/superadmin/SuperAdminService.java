package pl.edu.pk.pkampus.modules.superadmin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.events.DormEvent;
import pl.edu.pk.pkampus.modules.events.DormEventCategory;
import pl.edu.pk.pkampus.modules.events.DormEventRepository;
import pl.edu.pk.pkampus.modules.events.dto.DormEventDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.CreateCampusEventRequestDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.CreateDormAdminRequestDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.CreateDormitoryRequestDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.DormAdminDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.SuperAdminDormitoryDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.UpdateCampusEventRequestDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.UpdateDormAdminRequestDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.UpdateDormitoryRequestDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.TokenRevocationService;

import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private static final LocalTime DEFAULT_LAUNDRY_OPEN = LocalTime.of(7, 0);
    private static final LocalTime DEFAULT_LAUNDRY_CLOSE = LocalTime.of(23, 0);
    private static final int DEFAULT_SLOT_MINUTES = 180;
    private static final Set<DormEventCategory> CAMPUS_CATEGORIES = EnumSet.of(
            DormEventCategory.BED_LINEN,
            DormEventCategory.TECHNICAL_OUTAGE,
            DormEventCategory.ADMIN_NOTICE
    );
    private static final Set<UserStatus> ADS_PATCH_STATUSES = EnumSet.of(UserStatus.ACTIVE, UserStatus.BLOCKED);

    private final DormitoryRepository dormitoryRepository;
    private final UserRepository userRepository;
    private final DormEventRepository dormEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationService tokenRevocationService;

    @Transactional(readOnly = true)
    public List<SuperAdminDormitoryDto> listDormitories() {
        return dormitoryRepository.findAll().stream()
                .map(this::toDormitoryDto)
                .toList();
    }

    @Transactional
    public SuperAdminDormitoryDto createDormitory(CreateDormitoryRequestDto request) {
        String code = request.getCode().trim().toUpperCase();
        if (dormitoryRepository.findByCode(code).isPresent()) {
            throw new BusinessRuleException("Dormitory code already exists: " + code);
        }

        Dormitory dormitory = Dormitory.builder()
                .code(code)
                .name(request.getName().trim())
                .address(request.getAddress().trim())
                .floorsCount(request.getFloorsCount())
                .laundryOpeningTime(DEFAULT_LAUNDRY_OPEN)
                .laundryClosingTime(DEFAULT_LAUNDRY_CLOSE)
                .laundrySlotDurationMinutes(DEFAULT_SLOT_MINUTES)
                .build();

        Dormitory saved = dormitoryRepository.save(dormitory);
        log.info("Created dormitory {} ({})", saved.getCode(), saved.getId());
        return toDormitoryDto(saved);
    }

    @Transactional
    public SuperAdminDormitoryDto updateDormitory(UUID id, UpdateDormitoryRequestDto request) {
        Dormitory dormitory = dormitoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dormitory not found"));

        if (request.getCode() != null && !request.getCode().isBlank()) {
            String code = request.getCode().trim().toUpperCase();
            dormitoryRepository.findByCode(code)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new BusinessRuleException("Dormitory code already exists: " + code);
                    });
            dormitory.setCode(code);
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            dormitory.setName(request.getName().trim());
        }
        if (request.getAddress() != null && !request.getAddress().isBlank()) {
            dormitory.setAddress(request.getAddress().trim());
        }
        if (request.getFloorsCount() != null) {
            dormitory.setFloorsCount(request.getFloorsCount());
        }
        if (request.getLaundryOpeningTime() != null) {
            dormitory.setLaundryOpeningTime(request.getLaundryOpeningTime());
        }
        if (request.getLaundryClosingTime() != null) {
            dormitory.setLaundryClosingTime(request.getLaundryClosingTime());
        }
        if (request.getLaundrySlotDurationMinutes() != null) {
            dormitory.setLaundrySlotDurationMinutes(request.getLaundrySlotDurationMinutes());
        }

        if (!dormitory.getLaundryOpeningTime().isBefore(dormitory.getLaundryClosingTime())) {
            throw new BusinessRuleException("Laundry opening time must be before closing time");
        }

        return toDormitoryDto(dormitoryRepository.save(dormitory));
    }

    @Transactional(readOnly = true)
    public List<DormAdminDto> listDormAdmins() {
        return userRepository.findAllByRoleOrderByLastNameAscFirstNameAsc(UserRole.DORM_ADMIN).stream()
                .map(this::toDormAdminDto)
                .toList();
    }

    @Transactional
    public DormAdminDto createDormAdmin(CreateDormAdminRequestDto request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email is already registered");
        }

        Dormitory dormitory = dormitoryRepository.findById(request.getDormitoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Dormitory not found"));

        User admin = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phoneNumber(request.getPhoneNumber().trim())
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .dormitory(dormitory)
                .build();

        User saved = userRepository.save(admin);
        log.info("Created DORM_ADMIN {} for dormitory {}", saved.getEmail(), dormitory.getCode());
        return toDormAdminDto(saved);
    }

    @Transactional
    public DormAdminDto updateDormAdmin(UUID id, UpdateDormAdminRequestDto request) {
        User admin = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dormitory admin not found"));

        if (admin.getRole() != UserRole.DORM_ADMIN) {
            throw new BusinessRuleException("User is not a dormitory administrator");
        }

        if (request.getDormitoryId() != null) {
            Dormitory dormitory = dormitoryRepository.findById(request.getDormitoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Dormitory not found"));
            admin.setDormitory(dormitory);
        }
        if (request.getFirstName() != null && !request.getFirstName().isBlank()) {
            admin.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null && !request.getLastName().isBlank()) {
            admin.setLastName(request.getLastName().trim());
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            admin.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getStatus() != null) {
            if (!ADS_PATCH_STATUSES.contains(request.getStatus())) {
                throw new BusinessRuleException("Status must be ACTIVE or BLOCKED");
            }
            admin.setStatus(request.getStatus());
            if (request.getStatus() == UserStatus.BLOCKED) {
                tokenRevocationService.revokeUser(admin.getId());
            } else {
                tokenRevocationService.clearRevocation(admin.getId());
            }
        }

        return toDormAdminDto(userRepository.save(admin));
    }

    @Transactional(readOnly = true)
    public List<DormEventDto> listCampusEvents() {
        return dormEventRepository.findAllByDormitoryIsNullOrderByEventDateDesc().stream()
                .map(this::toEventDto)
                .toList();
    }

    @Transactional
    public DormEventDto createCampusEvent(User author, CreateCampusEventRequestDto request) {
        validateCampusCategory(request.getCategory());
        validateEventDates(request.getEventDate(), request.getEndDate());

        DormEvent event = DormEvent.builder()
                .author(author)
                .dormitory(null)
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .category(request.getCategory())
                .priority(request.getPriority())
                .pinned(true)
                .eventDate(request.getEventDate())
                .endDate(request.getEndDate())
                .build();

        DormEvent saved = dormEventRepository.save(event);
        log.info("Created campus event {} by {}", saved.getId(), author.getEmail());
        return toEventDto(saved);
    }

    @Transactional
    public DormEventDto updateCampusEvent(UUID id, UpdateCampusEventRequestDto request) {
        DormEvent event = dormEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (event.getDormitory() != null) {
            throw new BusinessRuleException("Only campus-wide events can be managed here");
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            event.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            event.setDescription(request.getDescription().trim());
        }
        if (request.getCategory() != null) {
            validateCampusCategory(request.getCategory());
            event.setCategory(request.getCategory());
        }
        if (request.getPriority() != null) {
            event.setPriority(request.getPriority());
        }
        // Official AOS/ADS notices stay pinned for resident visibility.
        event.setPinned(true);
        if (request.getEventDate() != null) {
            event.setEventDate(request.getEventDate());
        }
        if (request.getEndDate() != null) {
            event.setEndDate(request.getEndDate());
        }

        validateEventDates(event.getEventDate(), event.getEndDate());
        return toEventDto(dormEventRepository.save(event));
    }

    @Transactional
    public void deleteCampusEvent(UUID id) {
        DormEvent event = dormEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (event.getDormitory() != null) {
            throw new BusinessRuleException("Only campus-wide events can be managed here");
        }
        dormEventRepository.delete(event);
    }

    private void validateCampusCategory(DormEventCategory category) {
        if (!CAMPUS_CATEGORIES.contains(category)) {
            throw new BusinessRuleException("Campus events must use BED_LINEN, TECHNICAL_OUTAGE, or ADMIN_NOTICE");
        }
    }

    private void validateEventDates(java.time.Instant eventDate, java.time.Instant endDate) {
        if (endDate != null && endDate.isBefore(eventDate)) {
            throw new BusinessRuleException("Event end date must be on or after event date");
        }
    }

    private SuperAdminDormitoryDto toDormitoryDto(Dormitory d) {
        return SuperAdminDormitoryDto.builder()
                .id(d.getId())
                .name(d.getName())
                .code(d.getCode())
                .address(d.getAddress())
                .floorsCount(d.getFloorsCount())
                .laundryOpeningTime(d.getLaundryOpeningTime())
                .laundryClosingTime(d.getLaundryClosingTime())
                .laundrySlotDurationMinutes(d.getLaundrySlotDurationMinutes())
                .createdAt(d.getCreatedAt())
                .build();
    }

    private DormAdminDto toDormAdminDto(User user) {
        Dormitory dorm = user.getDormitory();
        return DormAdminDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .status(user.getStatus())
                .dormitoryId(dorm != null ? dorm.getId() : null)
                .dormitoryName(dorm != null ? dorm.getName() : null)
                .dormitoryCode(dorm != null ? dorm.getCode() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private DormEventDto toEventDto(DormEvent event) {
        User author = event.getAuthor();
        return DormEventDto.builder()
                .id(event.getId())
                .authorId(author != null ? author.getId() : null)
                .authorName(author != null ? author.getFirstName() + " " + author.getLastName() : null)
                .dormitoryId(null)
                .title(event.getTitle())
                .description(event.getDescription())
                .category(event.getCategory())
                .priority(event.getPriority())
                .pinned(event.isPinned())
                .eventDate(event.getEventDate())
                .endDate(event.getEndDate())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
