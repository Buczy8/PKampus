package pl.edu.pk.pkampus.modules.superadmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import pl.edu.pk.pkampus.modules.events.DormEventCategory;
import pl.edu.pk.pkampus.modules.events.DormEventPriority;
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
import pl.edu.pk.pkampus.security.config.JwtAuthenticationFilter;
import pl.edu.pk.pkampus.security.config.MustChangePasswordFilter;
import pl.edu.pk.pkampus.security.config.SecurityConfig;
import pl.edu.pk.pkampus.security.ratelimit.AuthRateLimitFilter;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SuperAdminController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("SuperAdminController slice tests")
class SuperAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SuperAdminService superAdminService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private MustChangePasswordFilter mustChangePasswordFilter;

    @MockitoBean
    private AuthRateLimitFilter authRateLimitFilter;

    @MockitoBean
    private UserRepository userRepository;

    private User superAdmin;

    @BeforeEach
    void setUp() {
        superAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("super@pk.edu.pl")
                .role(UserRole.SUPER_ADMIN)
                .status(UserStatus.ACTIVE)
                .firstName("Super")
                .lastName("Admin")
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(superAdmin, null, superAdmin.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Dormitories endpoints")
    class DormitoriesEndpointsTests {

        @Test
        @DisplayName("GET /api/v1/superadmin/dormitories returns 200 with list of dormitories")
        void listDormitoriesReturns200() throws Exception {
            SuperAdminDormitoryDto dormDto = SuperAdminDormitoryDto.builder()
                    .id(UUID.randomUUID())
                    .code("DS1")
                    .name("Dorm 1")
                    .address("ul. Testowa 1")
                    .floorsCount(4)
                    .laundryOpeningTime(LocalTime.of(7, 0))
                    .laundryClosingTime(LocalTime.of(23, 0))
                    .laundrySlotDurationMinutes(180)
                    .build();

            when(superAdminService.listDormitories()).thenReturn(List.of(dormDto));

            mockMvc.perform(get("/api/v1/superadmin/dormitories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].code").value("DS1"))
                    .andExpect(jsonPath("$.data[0].name").value("Dorm 1"));

            verify(superAdminService).listDormitories();
        }

        @Test
        @DisplayName("POST /api/v1/superadmin/dormitories returns 201 when valid")
        void createDormitoryReturns201() throws Exception {
            CreateDormitoryRequestDto request = CreateDormitoryRequestDto.builder()
                    .code("DS1")
                    .name("Dorm 1")
                    .address("ul. Testowa 1")
                    .floorsCount(4)
                    .build();

            SuperAdminDormitoryDto createdDto = SuperAdminDormitoryDto.builder()
                    .id(UUID.randomUUID())
                    .code("DS1")
                    .name("Dorm 1")
                    .address("ul. Testowa 1")
                    .floorsCount(4)
                    .build();

            when(superAdminService.createDormitory(any(CreateDormitoryRequestDto.class))).thenReturn(createdDto);

            mockMvc.perform(post("/api/v1/superadmin/dormitories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Dormitory created"))
                    .andExpect(jsonPath("$.data.code").value("DS1"));

            verify(superAdminService).createDormitory(any(CreateDormitoryRequestDto.class));
        }

        @Test
        @DisplayName("POST /api/v1/superadmin/dormitories returns 400 when invalid")
        void createDormitoryReturns400() throws Exception {
            CreateDormitoryRequestDto invalid = CreateDormitoryRequestDto.builder()
                    .code("")
                    .build();

            mockMvc.perform(post("/api/v1/superadmin/dormitories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PATCH /api/v1/superadmin/dormitories/{id} returns 200 when valid")
        void updateDormitoryReturns200() throws Exception {
            UUID dormId = UUID.randomUUID();
            UpdateDormitoryRequestDto request = UpdateDormitoryRequestDto.builder()
                    .name("Updated Dorm")
                    .floorsCount(5)
                    .build();

            SuperAdminDormitoryDto updatedDto = SuperAdminDormitoryDto.builder()
                    .id(dormId)
                    .code("DS1")
                    .name("Updated Dorm")
                    .floorsCount(5)
                    .build();

            when(superAdminService.updateDormitory(eq(dormId), any(UpdateDormitoryRequestDto.class)))
                    .thenReturn(updatedDto);

            mockMvc.perform(patch("/api/v1/superadmin/dormitories/{id}", dormId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("Updated Dorm"));

            verify(superAdminService).updateDormitory(eq(dormId), any(UpdateDormitoryRequestDto.class));
        }
    }

    @Nested
    @DisplayName("Dorm Admins endpoints")
    class DormAdminsEndpointsTests {

        @Test
        @DisplayName("GET /api/v1/superadmin/dorm-admins returns 200 with list")
        void listDormAdminsReturns200() throws Exception {
            DormAdminDto adminDto = DormAdminDto.builder()
                    .id(UUID.randomUUID())
                    .email("admin@pk.edu.pl")
                    .firstName("Adam")
                    .lastName("Nowak")
                    .status(UserStatus.ACTIVE)
                    .build();

            when(superAdminService.listDormAdmins()).thenReturn(List.of(adminDto));

            mockMvc.perform(get("/api/v1/superadmin/dorm-admins"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].email").value("admin@pk.edu.pl"));

            verify(superAdminService).listDormAdmins();
        }

        @Test
        @DisplayName("POST /api/v1/superadmin/dorm-admins returns 201 when valid")
        void createDormAdminReturns201() throws Exception {
            CreateDormAdminRequestDto request = CreateDormAdminRequestDto.builder()
                    .firstName("Anna")
                    .lastName("Kowalska")
                    .email("anna@pk.edu.pl")
                    .phoneNumber("+48123456789")
                    .password("Password123!")
                    .dormitoryId(UUID.randomUUID())
                    .build();

            DormAdminDto createdDto = DormAdminDto.builder()
                    .id(UUID.randomUUID())
                    .email("anna@pk.edu.pl")
                    .firstName("Anna")
                    .lastName("Kowalska")
                    .status(UserStatus.MUST_CHANGE_PASSWORD)
                    .build();

            when(superAdminService.createDormAdmin(any(CreateDormAdminRequestDto.class))).thenReturn(createdDto);

            mockMvc.perform(post("/api/v1/superadmin/dorm-admins")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Dormitory administrator created"))
                    .andExpect(jsonPath("$.data.email").value("anna@pk.edu.pl"));

            verify(superAdminService).createDormAdmin(any(CreateDormAdminRequestDto.class));
        }

        @Test
        @DisplayName("POST /api/v1/superadmin/dorm-admins returns 400 when invalid")
        void createDormAdminReturns400() throws Exception {
            CreateDormAdminRequestDto invalid = CreateDormAdminRequestDto.builder()
                    .email("invalid-email")
                    .build();

            mockMvc.perform(post("/api/v1/superadmin/dorm-admins")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PATCH /api/v1/superadmin/dorm-admins/{id} returns 200 when valid")
        void updateDormAdminReturns200() throws Exception {
            UUID adminId = UUID.randomUUID();
            UpdateDormAdminRequestDto request = UpdateDormAdminRequestDto.builder()
                    .status(UserStatus.BLOCKED)
                    .build();

            DormAdminDto updatedDto = DormAdminDto.builder()
                    .id(adminId)
                    .email("anna@pk.edu.pl")
                    .status(UserStatus.BLOCKED)
                    .build();

            when(superAdminService.updateDormAdmin(eq(adminId), any(UpdateDormAdminRequestDto.class)))
                    .thenReturn(updatedDto);

            mockMvc.perform(patch("/api/v1/superadmin/dorm-admins/{id}", adminId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("BLOCKED"));

            verify(superAdminService).updateDormAdmin(eq(adminId), any(UpdateDormAdminRequestDto.class));
        }
    }

    @Nested
    @DisplayName("Campus Events endpoints")
    class CampusEventsEndpointsTests {

        @Test
        @DisplayName("GET /api/v1/superadmin/events returns 200 with list")
        void listCampusEventsReturns200() throws Exception {
            DormEventDto eventDto = DormEventDto.builder()
                    .id(UUID.randomUUID())
                    .title("Notice")
                    .description("Desc")
                    .category(DormEventCategory.ADMIN_NOTICE)
                    .priority(DormEventPriority.INFO)
                    .pinned(true)
                    .build();

            when(superAdminService.listCampusEvents()).thenReturn(List.of(eventDto));

            mockMvc.perform(get("/api/v1/superadmin/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].title").value("Notice"));

            verify(superAdminService).listCampusEvents();
        }

        @Test
        @DisplayName("POST /api/v1/superadmin/events returns 201 when valid")
        void createCampusEventReturns201() throws Exception {
            CreateCampusEventRequestDto request = CreateCampusEventRequestDto.builder()
                    .title("Water outage")
                    .description("Maintenance outage")
                    .category(DormEventCategory.TECHNICAL_OUTAGE)
                    .priority(DormEventPriority.CRITICAL)
                    .pinned(true)
                    .eventDate(Instant.now())
                    .build();

            DormEventDto createdDto = DormEventDto.builder()
                    .id(UUID.randomUUID())
                    .title("Water outage")
                    .description("Maintenance outage")
                    .category(DormEventCategory.TECHNICAL_OUTAGE)
                    .priority(DormEventPriority.CRITICAL)
                    .pinned(true)
                    .build();

            when(superAdminService.createCampusEvent(any(), any(CreateCampusEventRequestDto.class)))
                    .thenReturn(createdDto);

            mockMvc.perform(post("/api/v1/superadmin/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Campus notice published"))
                    .andExpect(jsonPath("$.data.title").value("Water outage"));

            verify(superAdminService).createCampusEvent(any(), any(CreateCampusEventRequestDto.class));
        }

        @Test
        @DisplayName("POST /api/v1/superadmin/events returns 400 when invalid")
        void createCampusEventReturns400() throws Exception {
            CreateCampusEventRequestDto invalid = CreateCampusEventRequestDto.builder().build();

            mockMvc.perform(post("/api/v1/superadmin/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PATCH /api/v1/superadmin/events/{id} returns 200 when valid")
        void updateCampusEventReturns200() throws Exception {
            UUID eventId = UUID.randomUUID();
            UpdateCampusEventRequestDto request = UpdateCampusEventRequestDto.builder()
                    .title("Updated Outage")
                    .build();

            DormEventDto updatedDto = DormEventDto.builder()
                    .id(eventId)
                    .title("Updated Outage")
                    .build();

            when(superAdminService.updateCampusEvent(eq(eventId), any(UpdateCampusEventRequestDto.class)))
                    .thenReturn(updatedDto);

            mockMvc.perform(patch("/api/v1/superadmin/events/{id}", eventId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.title").value("Updated Outage"));

            verify(superAdminService).updateCampusEvent(eq(eventId), any(UpdateCampusEventRequestDto.class));
        }

        @Test
        @DisplayName("DELETE /api/v1/superadmin/events/{id} returns 200")
        void deleteCampusEventReturns200() throws Exception {
            UUID eventId = UUID.randomUUID();

            mockMvc.perform(delete("/api/v1/superadmin/events/{id}", eventId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Campus notice deleted"));

            verify(superAdminService).deleteCampusEvent(eventId);
        }
    }
}
