package pl.edu.pk.pkampus.modules.admin.receptionists;

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
import pl.edu.pk.pkampus.modules.admin.dto.CreateReceptionistRequestDto;
import pl.edu.pk.pkampus.modules.admin.dto.ReceptionistDto;
import pl.edu.pk.pkampus.modules.admin.dto.UpdateReceptionistRequestDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.config.JwtAuthenticationFilter;
import pl.edu.pk.pkampus.security.config.MustChangePasswordFilter;
import pl.edu.pk.pkampus.security.config.SecurityConfig;
import pl.edu.pk.pkampus.security.ratelimit.AuthRateLimitFilter;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminReceptionistController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminReceptionistController unit tests")
class AdminReceptionistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminReceptionistService adminReceptionistService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private MustChangePasswordFilter mustChangePasswordFilter;

    @MockitoBean
    private AuthRateLimitFilter authRateLimitFilter;

    @MockitoBean
    private UserRepository userRepository;

    private User admin;
    private UUID receptionistId;

    @BeforeEach
    void setUp() {
        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        receptionistId = UUID.randomUUID();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listReceptionistsReturnsOk() throws Exception {
        when(adminReceptionistService.list(any())).thenReturn(List.of(ReceptionistDto.builder()
                .id(receptionistId)
                .email("portier@pk.edu.pl")
                .firstName("Stanisław")
                .lastName("Kowalski")
                .status(UserStatus.ACTIVE)
                .build()));

        mockMvc.perform(get("/api/v1/admin/receptionists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].email").value("portier@pk.edu.pl"))
                .andExpect(jsonPath("$.data[0].firstName").value("Stanisław"));
    }

    @Test
    void createReceptionistReturnsCreated() throws Exception {
        CreateReceptionistRequestDto request = CreateReceptionistRequestDto.builder()
                .firstName("Jan")
                .lastName("Nowak")
                .email("jan.nowak@pk.edu.pl")
                .phoneNumber("+48123456789")
                .password("Password123!")
                .build();

        when(adminReceptionistService.create(any(), any())).thenReturn(ReceptionistDto.builder()
                .id(receptionistId)
                .email("jan.nowak@pk.edu.pl")
                .status(UserStatus.MUST_CHANGE_PASSWORD)
                .build());

        mockMvc.perform(post("/api/v1/admin/receptionists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Receptionist account created"))
                .andExpect(jsonPath("$.data.email").value("jan.nowak@pk.edu.pl"));
    }

    @Test
    void createReceptionistRejectsInvalidDto() throws Exception {
        CreateReceptionistRequestDto invalid = CreateReceptionistRequestDto.builder()
                .firstName("")
                .lastName("")
                .email("invalid-email")
                .phoneNumber("abc")
                .password("weak")
                .build();

        mockMvc.perform(post("/api/v1/admin/receptionists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReceptionistReturnsOk() throws Exception {
        UpdateReceptionistRequestDto request = UpdateReceptionistRequestDto.builder()
                .status(UserStatus.BLOCKED)
                .build();

        when(adminReceptionistService.update(any(), eq(receptionistId), any()))
                .thenReturn(ReceptionistDto.builder()
                        .id(receptionistId)
                        .status(UserStatus.BLOCKED)
                        .build());

        mockMvc.perform(patch("/api/v1/admin/receptionists/" + receptionistId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("BLOCKED"));
    }

    @Test
    void updateReceptionistPropagatesNotFound() throws Exception {
        UpdateReceptionistRequestDto request = UpdateReceptionistRequestDto.builder()
                .status(UserStatus.BLOCKED)
                .build();

        when(adminReceptionistService.update(any(), eq(receptionistId), any()))
                .thenThrow(new ResourceNotFoundException("Receptionist not found"));

        mockMvc.perform(patch("/api/v1/admin/receptionists/" + receptionistId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateReceptionistPropagatesBusinessRuleException() throws Exception {
        UpdateReceptionistRequestDto request = UpdateReceptionistRequestDto.builder()
                .status(UserStatus.ACTIVE)
                .build();

        when(adminReceptionistService.update(any(), eq(receptionistId), any()))
                .thenThrow(new BusinessRuleException("Status must be ACTIVE or BLOCKED"));

        mockMvc.perform(patch("/api/v1/admin/receptionists/" + receptionistId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }
}
