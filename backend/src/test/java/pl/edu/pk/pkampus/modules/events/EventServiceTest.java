package pl.edu.pk.pkampus.modules.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.events.dto.CreateDormEventRequestDto;
import pl.edu.pk.pkampus.modules.events.dto.DormEventDto;
import pl.edu.pk.pkampus.modules.events.dto.UpdateDormEventRequestDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventService unit tests (AAA)")
class EventServiceTest {

    @Mock
    private DormEventRepository dormEventRepository;

    @InjectMocks
    private EventService eventService;

    private Dormitory dormitory;
    private User staffAdmin;
    private User staffReceptionist;
    private User resident;
    private DormEvent event;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        dormitory = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS-1")
                .build();

        staffAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
                .firstName("Marian")
                .lastName("Kierownik")
                .role(UserRole.DORM_ADMIN)
                .dormitory(dormitory)
                .build();

        staffReceptionist = User.builder()
                .id(UUID.randomUUID())
                .email("portier@pk.edu.pl")
                .firstName("Jan")
                .lastName("Portier")
                .role(UserRole.RECEPTIONIST)
                .dormitory(dormitory)
                .build();

        resident = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .firstName("Tomasz")
                .lastName("Kowalski")
                .role(UserRole.RESIDENT)
                .dormitory(dormitory)
                .build();

        eventId = UUID.randomUUID();
        event = DormEvent.builder()
                .id(eventId)
                .author(staffAdmin)
                .dormitory(dormitory)
                .title("Przegląd instalacji gazowej")
                .description("W poniedziałek od 8:00")
                .category(DormEventCategory.ADMIN_NOTICE)
                .priority(DormEventPriority.WARNING)
                .pinned(true)
                .eventDate(Instant.now().plusSeconds(3600))
                .endDate(Instant.now().plusSeconds(7200))
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("findActiveBanner")
    class FindActiveBanner {

        @Test
        @DisplayName("Should find active banner candidate for user with dormitory")
        void findActiveBannerWithDormitory() {
            // Arrange
            when(dormEventRepository.findActiveBannerCandidates(any(Instant.class), eq(dormitory.getId())))
                    .thenReturn(List.of(event));

            // Act
            Optional<DormEventDto> result = eventService.findActiveBanner(resident);

            // Assert
            assertTrue(result.isPresent());
            assertEquals(eventId, result.get().getId());
            assertEquals("Przegląd instalacji gazowej", result.get().getTitle());
            assertEquals(DormEventPriority.WARNING, result.get().getPriority());
        }

        @Test
        @DisplayName("Should find active campus banner for user without dormitory")
        void findActiveBannerWithoutDormitory() {
            // Arrange
            resident.setDormitory(null);
            when(dormEventRepository.findActiveCampusBannerCandidates(any(Instant.class)))
                    .thenReturn(List.of(event));

            // Act
            Optional<DormEventDto> result = eventService.findActiveBanner(resident);

            // Assert
            assertTrue(result.isPresent());
            assertEquals(eventId, result.get().getId());
        }

        @Test
        @DisplayName("Should return empty optional when no banner candidates exist")
        void findActiveBannerEmpty() {
            // Arrange
            when(dormEventRepository.findActiveBannerCandidates(any(Instant.class), eq(dormitory.getId())))
                    .thenReturn(List.of());

            // Act
            Optional<DormEventDto> result = eventService.findActiveBanner(resident);

            // Assert
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("listVisible")
    class ListVisible {

        @Test
        @DisplayName("Should list visible events for user's dormitory")
        void listVisibleForDormitory() {
            // Arrange
            when(dormEventRepository.findVisibleForDormitory(eq(dormitory.getId()), any(Instant.class)))
                    .thenReturn(List.of(event));

            // Act
            List<DormEventDto> result = eventService.listVisible(resident);

            // Assert
            assertEquals(1, result.size());
            assertEquals(eventId, result.getFirst().getId());
        }

        @Test
        @DisplayName("Should list campus-only visible events when user has no dormitory")
        void listVisibleCampusOnly() {
            // Arrange
            resident.setDormitory(null);
            when(dormEventRepository.findVisibleCampusOnly(any(Instant.class)))
                    .thenReturn(List.of(event));

            // Act
            List<DormEventDto> result = eventService.listVisible(resident);

            // Assert
            assertEquals(1, result.size());
            assertEquals(eventId, result.getFirst().getId());
        }
    }

    @Nested
    @DisplayName("listForStaff")
    class ListForStaff {

        @Test
        @DisplayName("Should list notices for staff member in assigned dormitory")
        void listForStaffSuccess() {
            // Arrange
            when(dormEventRepository.findAllByDormitoryIdOrderByEventDateDesc(dormitory.getId()))
                    .thenReturn(List.of(event));

            // Act
            List<DormEventDto> result = eventService.listForStaff(staffReceptionist);

            // Assert
            assertEquals(1, result.size());
            assertEquals(eventId, result.getFirst().getId());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when caller is not staff")
        void listForStaffThrowsWhenNotStaff() {
            // Arrange & Act & Assert
            assertThrows(AccessDeniedException.class, () -> eventService.listForStaff(resident));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when staff member has no assigned dormitory")
        void listForStaffThrowsWhenNoDormitory() {
            // Arrange
            staffAdmin.setDormitory(null);

            // Act & Assert
            assertThrows(BusinessRuleException.class, () -> eventService.listForStaff(staffAdmin));
        }
    }

    @Nested
    @DisplayName("createForStaff")
    class CreateForStaff {

        @Test
        @DisplayName("Should create notice for staff successfully")
        void createForStaffSuccess() {
            // Arrange
            Instant eventDate = Instant.now().plusSeconds(3600);
            Instant endDate = eventDate.plusSeconds(3600);

            CreateDormEventRequestDto request = CreateDormEventRequestDto.builder()
                    .title("  Awaria windy  ")
                    .description("  Serwis wezwany  ")
                    .priority(DormEventPriority.CRITICAL)
                    .eventDate(eventDate)
                    .endDate(endDate)
                    .build();

            when(dormEventRepository.save(any(DormEvent.class))).thenAnswer(inv -> {
                DormEvent e = inv.getArgument(0);
                e.setId(UUID.randomUUID());
                return e;
            });

            // Act
            DormEventDto result = eventService.createForStaff(staffAdmin, request);

            // Assert
            assertNotNull(result);
            assertEquals("Awaria windy", result.getTitle());
            assertEquals("Serwis wezwany", result.getDescription());
            assertEquals(DormEventPriority.CRITICAL, result.getPriority());
            assertTrue(result.isPinned());
            assertEquals(DormEventCategory.ADMIN_NOTICE, result.getCategory());

            ArgumentCaptor<DormEvent> captor = ArgumentCaptor.forClass(DormEvent.class);
            verify(dormEventRepository).save(captor.capture());
            DormEvent saved = captor.getValue();
            assertEquals("Awaria windy", saved.getTitle());
            assertEquals(staffAdmin, saved.getAuthor());
            assertEquals(dormitory, saved.getDormitory());
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when endDate is before eventDate")
        void createForStaffThrowsOnInvalidDates() {
            // Arrange
            Instant eventDate = Instant.now().plusSeconds(3600);
            Instant endDate = eventDate.minusSeconds(100);

            CreateDormEventRequestDto request = CreateDormEventRequestDto.builder()
                    .title("Błędne daty")
                    .description("Opis")
                    .priority(DormEventPriority.INFO)
                    .eventDate(eventDate)
                    .endDate(endDate)
                    .build();

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> eventService.createForStaff(staffAdmin, request));
            assertEquals("Event end date must be on or after event date", ex.getMessage());
            verify(dormEventRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateForStaff")
    class UpdateForStaff {

        @Test
        @DisplayName("Should update notice fields for staff")
        void updateForStaffSuccess() {
            // Arrange
            Instant newEventDate = Instant.now().plusSeconds(5000);
            Instant newEndDate = newEventDate.plusSeconds(3000);

            UpdateDormEventRequestDto request = UpdateDormEventRequestDto.builder()
                    .title("  Zaktualizowany tytuł  ")
                    .description("  Nowy opis  ")
                    .priority(DormEventPriority.CRITICAL)
                    .eventDate(newEventDate)
                    .endDate(newEndDate)
                    .build();

            when(dormEventRepository.findByIdAndDormitoryId(eventId, dormitory.getId()))
                    .thenReturn(Optional.of(event));
            when(dormEventRepository.save(any(DormEvent.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            DormEventDto result = eventService.updateForStaff(staffAdmin, eventId, request);

            // Assert
            assertEquals("Zaktualizowany tytuł", result.getTitle());
            assertEquals("Nowy opis", result.getDescription());
            assertEquals(DormEventPriority.CRITICAL, result.getPriority());
            assertEquals(newEventDate, result.getEventDate());
            assertEquals(newEndDate, result.getEndDate());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when notice not found in dormitory")
        void updateForStaffThrowsWhenNotFound() {
            // Arrange
            UUID notFoundId = UUID.randomUUID();
            UpdateDormEventRequestDto request = UpdateDormEventRequestDto.builder().build();

            when(dormEventRepository.findByIdAndDormitoryId(notFoundId, dormitory.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> eventService.updateForStaff(staffAdmin, notFoundId, request));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when update produces invalid dates")
        void updateForStaffThrowsOnInvalidDates() {
            // Arrange
            Instant invalidEndDate = event.getEventDate().minusSeconds(100);
            UpdateDormEventRequestDto request = UpdateDormEventRequestDto.builder()
                    .endDate(invalidEndDate)
                    .build();

            when(dormEventRepository.findByIdAndDormitoryId(eventId, dormitory.getId()))
                    .thenReturn(Optional.of(event));

            // Act & Assert
            assertThrows(BusinessRuleException.class,
                    () -> eventService.updateForStaff(staffAdmin, eventId, request));
            verify(dormEventRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteForStaff")
    class DeleteForStaff {

        @Test
        @DisplayName("Should delete notice for staff successfully")
        void deleteForStaffSuccess() {
            // Arrange
            when(dormEventRepository.findByIdAndDormitoryId(eventId, dormitory.getId()))
                    .thenReturn(Optional.of(event));

            // Act
            eventService.deleteForStaff(staffAdmin, eventId);

            // Assert
            verify(dormEventRepository).delete(event);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when event not found in dormitory")
        void deleteForStaffThrowsWhenNotFound() {
            // Arrange
            UUID notFoundId = UUID.randomUUID();
            when(dormEventRepository.findByIdAndDormitoryId(notFoundId, dormitory.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> eventService.deleteForStaff(staffAdmin, notFoundId));
            verify(dormEventRepository, never()).delete(any());
        }
    }
}
