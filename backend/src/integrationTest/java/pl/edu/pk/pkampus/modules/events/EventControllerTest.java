package pl.edu.pk.pkampus.modules.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.edu.pk.pkampus.modules.events.dto.DormEventDto;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("EventController slice tests")
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

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

    private User resident;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        resident = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .build();
        eventId = UUID.randomUUID();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(resident, null, resident.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/events returns visible notices")
    void listReturnsVisibleNotices() throws Exception {
        DormEventDto dto = DormEventDto.builder()
                .id(eventId)
                .title("Przegląd wind")
                .priority(DormEventPriority.INFO)
                .eventDate(Instant.now())
                .build();

        when(eventService.listVisible(any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$.data[0].title").value("Przegląd wind"));
    }

    @Test
    @DisplayName("GET /api/v1/events/banner returns banner when present")
    void getBannerReturnsBanner() throws Exception {
        DormEventDto dto = DormEventDto.builder()
                .id(eventId)
                .title("Ewakuacja")
                .priority(DormEventPriority.CRITICAL)
                .build();

        when(eventService.findActiveBanner(any())).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/api/v1/events/banner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(eventId.toString()))
                .andExpect(jsonPath("$.data.title").value("Ewakuacja"));
    }

    @Test
    @DisplayName("GET /api/v1/events/banner returns null data when no banner")
    void getBannerReturnsNull() throws Exception {
        when(eventService.findActiveBanner(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/events/banner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
