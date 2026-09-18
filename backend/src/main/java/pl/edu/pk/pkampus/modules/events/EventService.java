package pl.edu.pk.pkampus.modules.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.modules.events.dto.DormEventDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

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
