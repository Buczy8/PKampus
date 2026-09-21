package pl.edu.pk.pkampus.modules.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.events.dto.CreateDormEventRequestDto;
import pl.edu.pk.pkampus.modules.events.dto.DormEventDto;
import pl.edu.pk.pkampus.modules.events.dto.UpdateDormEventRequestDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private static final Set<UserRole> STAFF_ROLES = EnumSet.of(
            UserRole.RECEPTIONIST,
            UserRole.DORM_ADMIN
    );

    private final DormEventRepository dormEventRepository;

    @Transactional(readOnly = true)
    public Optional<DormEventDto> findActiveBanner(User user) {
        Instant now = Instant.now();
        UUID dormitoryId = user.getDormitory() != null ? user.getDormitory().getId() : null;

        List<DormEvent> candidates = dormitoryId != null
                ? dormEventRepository.findActiveBannerCandidates(now, dormitoryId)
                : dormEventRepository.findActiveCampusBannerCandidates(now);

        return candidates.stream().findFirst().map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<DormEventDto> listVisible(User user) {
        Instant now = Instant.now();
        if (user.getDormitory() == null) {
            return dormEventRepository.findVisibleCampusOnly(now).stream()
                    .map(this::toDto)
                    .toList();
        }
        return dormEventRepository.findVisibleForDormitory(user.getDormitory().getId(), now).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DormEventDto> listForStaff(User staff) {
        UUID dormitoryId = requireStaffDormitoryId(staff);
        return dormEventRepository.findAllByDormitoryIdOrderByEventDateDesc(dormitoryId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public DormEventDto createForStaff(User staff, CreateDormEventRequestDto request) {
        requireStaffDormitoryId(staff);
        Dormitory dormitory = staff.getDormitory();
        validateEventDates(request.getEventDate(), request.getEndDate());

        DormEvent event = DormEvent.builder()
                .author(staff)
                .dormitory(dormitory)
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .category(DormEventCategory.ADMIN_NOTICE)
                .priority(request.getPriority())
                .pinned(true)
                .eventDate(request.getEventDate())
                .endDate(request.getEndDate())
                .build();

        DormEvent saved = dormEventRepository.save(event);
        log.info("Staff {} published dorm notice {} for dormitory {}",
                staff.getEmail(), saved.getId(), dormitory.getId());
        return toDto(saved);
    }

    @Transactional
    public DormEventDto updateForStaff(User staff, UUID id, UpdateDormEventRequestDto request) {
        UUID dormitoryId = requireStaffDormitoryId(staff);
        DormEvent event = dormEventRepository.findByIdAndDormitoryId(id, dormitoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            event.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            event.setDescription(request.getDescription().trim());
        }
        if (request.getPriority() != null) {
            event.setPriority(request.getPriority());
        }
        event.setPinned(true);
        event.setCategory(DormEventCategory.ADMIN_NOTICE);
        if (request.getEventDate() != null) {
            event.setEventDate(request.getEventDate());
        }
        if (request.getEndDate() != null) {
            event.setEndDate(request.getEndDate());
        }

        validateEventDates(event.getEventDate(), event.getEndDate());
        return toDto(dormEventRepository.save(event));
    }

    @Transactional
    public void deleteForStaff(User staff, UUID id) {
        UUID dormitoryId = requireStaffDormitoryId(staff);
        DormEvent event = dormEventRepository.findByIdAndDormitoryId(id, dormitoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        dormEventRepository.delete(event);
        log.info("Staff {} deleted dorm notice {}", staff.getEmail(), id);
    }

    private UUID requireStaffDormitoryId(User staff) {
        if (!STAFF_ROLES.contains(staff.getRole())) {
            throw new AccessDeniedException("Only receptionist or dormitory admin can manage dorm notices");
        }
        if (staff.getDormitory() == null) {
            throw new BusinessRuleException("Staff account has no dormitory assigned");
        }
        return staff.getDormitory().getId();
    }

    private void validateEventDates(Instant eventDate, Instant endDate) {
        if (endDate != null && endDate.isBefore(eventDate)) {
            throw new BusinessRuleException("Event end date must be on or after event date");
        }
    }

    private DormEventDto toDto(DormEvent event) {
        User author = event.getAuthor();
        return DormEventDto.builder()
                .id(event.getId())
                .authorId(author != null ? author.getId() : null)
                .authorName(author != null ? author.getFirstName() + " " + author.getLastName() : null)
                .dormitoryId(event.getDormitory() != null ? event.getDormitory().getId() : null)
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
