package pl.edu.pk.pkampus.modules.laundry;

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
import pl.edu.pk.pkampus.modules.laundry.dto.AdminLaundryMachineDto;
import pl.edu.pk.pkampus.modules.laundry.dto.CreateLaundryMachineRequestDto;
import pl.edu.pk.pkampus.modules.laundry.dto.UpdateLaundryMachineRequestDto;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminLaundryMachineController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminLaundryMachineController slice tests")
class AdminLaundryMachineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminLaundryMachineService adminLaundryMachineService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private MustChangePasswordFilter mustChangePasswordFilter;

    @MockitoBean
    private AuthRateLimitFilter authRateLimitFilter;

    @MockitoBean
    private UserRepository userRepository;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/admin/laundry-machines returns machine list")
    void listReturnsMachines() throws Exception {
        UUID machineId = UUID.randomUUID();
        AdminLaundryMachineDto dto = AdminLaundryMachineDto.builder()
                .id(machineId)
                .dormitoryId(UUID.randomUUID())
                .machineIdentifier("Pralka 1")
                .floorLocation("Parter")
                .status(LaundryMachineStatus.AVAILABLE)
                .createdAt(Instant.now())
                .build();

        when(adminLaundryMachineService.listForAdmin(any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/admin/laundry-machines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(machineId.toString()))
                .andExpect(jsonPath("$.data[0].machineIdentifier").value("Pralka 1"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/laundry-machines creates machine and returns 201 Created")
    void createReturns201() throws Exception {
        CreateLaundryMachineRequestDto request = CreateLaundryMachineRequestDto.builder()
                .machineIdentifier("Pralka 1")
                .floorLocation("Parter")
                .build();

        UUID machineId = UUID.randomUUID();
        AdminLaundryMachineDto dto = AdminLaundryMachineDto.builder()
                .id(machineId)
                .dormitoryId(UUID.randomUUID())
                .machineIdentifier("Pralka 1")
                .floorLocation("Parter")
                .status(LaundryMachineStatus.AVAILABLE)
                .createdAt(Instant.now())
                .build();

        when(adminLaundryMachineService.create(any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/admin/laundry-machines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(machineId.toString()))
                .andExpect(jsonPath("$.message").value("Laundry machine created"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/laundry-machines fails validation on blank fields")
    void createFailsValidation() throws Exception {
        CreateLaundryMachineRequestDto invalidRequest = CreateLaundryMachineRequestDto.builder()
                .machineIdentifier("")
                .floorLocation("")
                .build();

        mockMvc.perform(post("/api/v1/admin/laundry-machines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/laundry-machines/{id} updates machine")
    void updateReturns200() throws Exception {
        UUID machineId = UUID.randomUUID();
        UpdateLaundryMachineRequestDto request = UpdateLaundryMachineRequestDto.builder()
                .machineIdentifier("Pralka 1 (nowa)")
                .status(LaundryMachineStatus.OUT_OF_ORDER)
                .build();

        AdminLaundryMachineDto dto = AdminLaundryMachineDto.builder()
                .id(machineId)
                .dormitoryId(UUID.randomUUID())
                .machineIdentifier("Pralka 1 (nowa)")
                .floorLocation("Parter")
                .status(LaundryMachineStatus.OUT_OF_ORDER)
                .createdAt(Instant.now())
                .build();

        when(adminLaundryMachineService.update(any(), eq(machineId), any())).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/admin/laundry-machines/" + machineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("OUT_OF_ORDER"));

        verify(adminLaundryMachineService).update(any(), eq(machineId), any());
    }
}
