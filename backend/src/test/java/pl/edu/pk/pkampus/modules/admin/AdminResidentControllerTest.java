package pl.edu.pk.pkampus.modules.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminResidentController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminResidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminResidentService adminResidentService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private AuthRateLimitFilter authRateLimitFilter;

    @MockBean
    private UserRepository userRepository;

    @Test
    void shouldListPending() throws Exception {
        User admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        when(adminResidentService.listPendingResidents(any()))
                .thenReturn(List.of(PendingResidentDto.builder()
                        .id(UUID.randomUUID())
                        .email("student@pk.edu.pl")
                        .declaredRoomNumber("101")
                        .build()));

        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        admin, null, admin.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            mockMvc.perform(get("/api/v1/admin/residents/pending"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].email").value("student@pk.edu.pl"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldActivateResident() throws Exception {
        User admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        UUID residentId = UUID.randomUUID();

        when(adminResidentService.activateResident(any(), eq(residentId), any()))
                .thenReturn(ActivateResidentResponseDto.builder()
                        .userId(residentId)
                        .status(UserStatus.ACTIVE)
                        .roomNumber("101")
                        .academicYear("2025/2026")
                        .message("Residency approved. Account is now ACTIVE.")
                        .build());

        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        admin, null, admin.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            mockMvc.perform(post("/api/v1/admin/residents/" + residentId + "/activate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.roomNumber").value("101"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldRejectResident() throws Exception {
        User admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        UUID residentId = UUID.randomUUID();

        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        admin, null, admin.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            mockMvc.perform(post("/api/v1/admin/residents/" + residentId + "/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    java.util.Map.of("reason", "Not on housing list"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
