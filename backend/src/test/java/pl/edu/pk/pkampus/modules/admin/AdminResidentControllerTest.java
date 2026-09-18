package pl.edu.pk.pkampus.modules.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.admin.dto.ActivateResidentResponseDto;
import pl.edu.pk.pkampus.modules.admin.dto.PendingResidentDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.config.JwtAuthenticationFilter;
import pl.edu.pk.pkampus.security.config.SecurityConfig;
import pl.edu.pk.pkampus.security.ratelimit.AuthRateLimitFilter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminResidentController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminResidentController unit tests")
class AdminResidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminResidentService adminResidentService;

    @MockBean
    private AdminResidentDirectoryService adminResidentDirectoryService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private AuthRateLimitFilter authRateLimitFilter;

    @MockBean
    private UserRepository userRepository;

    private User admin;
    private UUID residentId;

    @BeforeEach
    void setUp() {
        admin = User.builder()
                .id(UUID.randomUUID())
                .email("kierownik@pk.edu.pl")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        residentId = UUID.randomUUID();

        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        admin, null, admin.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listPendingReturnsOk() throws Exception {
        when(adminResidentService.listPendingResidents(any()))
                .thenReturn(List.of(PendingResidentDto.builder()
                        .id(residentId)
                        .email("student@pk.edu.pl")
                        .declaredRoomNumber("101")
                        .build()));

        mockMvc.perform(get("/api/v1/admin/residents/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].email").value("student@pk.edu.pl"))
                .andExpect(jsonPath("$.data[0].declaredRoomNumber").value("101"));
    }

    @Test
    void activateReturnsOk() throws Exception {
        when(adminResidentService.activateResident(any(), eq(residentId), any()))
                .thenReturn(ActivateResidentResponseDto.builder()
                        .userId(residentId)
                        .status(UserStatus.ACTIVE)
                        .roomNumber("101")
                        .academicYear("2025/2026")
                        .message("Residency approved. Account is now ACTIVE.")
                        .build());

        mockMvc.perform(post("/api/v1/admin/residents/" + residentId + "/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.roomNumber").value("101"))
                .andExpect(jsonPath("$.data.academicYear").value("2025/2026"));
    }

    @Test
    void activateWithoutBodyReturnsOk() throws Exception {
        when(adminResidentService.activateResident(any(), eq(residentId), any()))
                .thenReturn(ActivateResidentResponseDto.builder()
                        .userId(residentId)
                        .status(UserStatus.ACTIVE)
                        .roomNumber("101")
                        .academicYear("2025/2026")
                        .message("ok")
                        .build());

        mockMvc.perform(post("/api/v1/admin/residents/" + residentId + "/activate"))
                .andExpect(status().isOk());
    }

    @Test
    void activatePropagatesNotFound() throws Exception {
        when(adminResidentService.activateResident(any(), eq(residentId), any()))
                .thenThrow(new ResourceNotFoundException("Resident application not found"));

        mockMvc.perform(post("/api/v1/admin/residents/" + residentId + "/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void activatePropagatesAccountStatusConflict() throws Exception {
        when(adminResidentService.activateResident(any(), eq(residentId), any()))
                .thenThrow(new AccountStatusException("Resident is not awaiting approval"));

        mockMvc.perform(post("/api/v1/admin/residents/" + residentId + "/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectReturnsOk() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + residentId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Not on housing list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminResidentService).rejectResident(any(), eq(residentId), any());
    }

    @Test
    void rejectRequiresReason() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + residentId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectRejectsBlankReason() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + residentId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectPropagatesServiceErrors() throws Exception {
        doThrow(new ResourceNotFoundException("Resident application not found"))
                .when(adminResidentService).rejectResident(any(), eq(residentId), any());

        mockMvc.perform(post("/api/v1/admin/residents/" + residentId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "x"))))
                .andExpect(status().isNotFound());
    }
}
