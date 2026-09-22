package pl.edu.pk.pkampus.modules.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.events.dto.CreateDormEventRequestDto;
import pl.edu.pk.pkampus.modules.events.dto.DormEventDto;
import pl.edu.pk.pkampus.modules.events.dto.UpdateDormEventRequestDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.config.JwtAuthenticationFilter;
import pl.edu.pk.pkampus.security.config.MustChangePasswordFilter;
import pl.edu.pk.pkampus.security.config.SecurityConfig;
import pl.edu.pk.pkampus.security.ratelimit.AuthRateLimitFilter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDormEventController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminDormEventController slice tests")
class AdminDormEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private MustChangePasswordFilter mustChangePasswordFilter;

    @MockitoBean
    private AuthRateLimitFilter authRateLimitFilter;

    @MockitoBean
    private UserRepository userRepository;

    private User admin;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        eventId = UUID.randomUUID();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/admin/events returns notices for admin's dormitory")
    void listReturnsNotices() throws Exception {
        DormEventDto dto = DormEventDto.builder()
                .id(eventId)
                .title("Informacja")
                .priority(DormEventPriority.INFO)
                .build();

        when(eventService.listForStaff(any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/admin/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$.data[0].title").value("Informacja"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/events returns 201 on valid body")
    void createReturnsCreated() throws Exception {
        CreateDormEventRequestDto request = CreateDormEventRequestDto.builder()
                .title("Awaria windy")
                .description("Winda nieczynna")
                .priority(DormEventPriority.CRITICAL)
                .eventDate(Instant.now().plusSeconds(3600))
                .build();

        DormEventDto created = DormEventDto.builder()
                .id(eventId)
                .title("Awaria windy")
                .priority(DormEventPriority.CRITICAL)
                .build();

        when(eventService.createForStaff(any(), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Dorm notice published"))
                .andExpect(jsonPath("$.data.title").value("Awaria windy"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/events returns 400 on invalid body")
    void createRejectsInvalidBody() throws Exception {
        CreateDormEventRequestDto invalid = CreateDormEventRequestDto.builder()
                .title("")
                .description("")
                .priority(null)
                .eventDate(null)
                .build();

        mockMvc.perform(post("/api/v1/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/events/{id} returns updated notice")
    void updateReturnsOk() throws Exception {
        UpdateDormEventRequestDto request = UpdateDormEventRequestDto.builder()
                .title("Nowy tytuł")
                .build();

        DormEventDto updated = DormEventDto.builder()
                .id(eventId)
                .title("Nowy tytuł")
                .build();

        when(eventService.updateForStaff(any(), eq(eventId), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/admin/events/" + eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Nowy tytuł"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/events/{id} propagates ResourceNotFoundException")
    void updatePropagatesNotFound() throws Exception {
        UpdateDormEventRequestDto request = UpdateDormEventRequestDto.builder()
                .title("Nowy tytuł")
                .build();

        when(eventService.updateForStaff(any(), eq(eventId), any()))
                .thenThrow(new ResourceNotFoundException("Event not found"));

        mockMvc.perform(patch("/api/v1/admin/events/" + eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/events/{id} propagates BusinessRuleException")
    void updatePropagatesBusinessRuleException() throws Exception {
        UpdateDormEventRequestDto request = UpdateDormEventRequestDto.builder()
                .title("Nowy tytuł")
                .build();

        when(eventService.updateForStaff(any(), eq(eventId), any()))
                .thenThrow(new BusinessRuleException("Event end date must be on or after event date"));

        mockMvc.perform(patch("/api/v1/admin/events/" + eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/events/{id} returns 200 on success")
    void deleteReturnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/events/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Dorm notice deleted"));

        verify(eventService).deleteForStaff(any(), eq(eventId));
    }
}
