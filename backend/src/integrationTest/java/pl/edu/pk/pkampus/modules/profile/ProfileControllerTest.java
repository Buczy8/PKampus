package pl.edu.pk.pkampus.modules.profile;

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
import pl.edu.pk.pkampus.modules.profile.dto.ResidentCardDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.config.JwtAuthenticationFilter;
import pl.edu.pk.pkampus.security.config.MustChangePasswordFilter;
import pl.edu.pk.pkampus.security.config.SecurityConfig;
import pl.edu.pk.pkampus.security.ratelimit.AuthRateLimitFilter;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ProfileController slice tests")
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileCardService profileCardService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private MustChangePasswordFilter mustChangePasswordFilter;

    @MockitoBean
    private AuthRateLimitFilter authRateLimitFilter;

    @MockitoBean
    private UserRepository userRepository;

    private User resident;

    @BeforeEach
    void setUp() {
        resident = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(resident, null, resident.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/profile/card returns 200 OK and resident card DTO")
    void getCardReturnsOk() throws Exception {
        ResidentCardDto card = ResidentCardDto.builder()
                .firstName("Jan")
                .lastName("Kowalski")
                .dormitoryName("DS Alfa")
                .dormitoryCode("ALF")
                .roomNumber("205")
                .academicYear("2026/2027")
                .status("ACTIVE")
                .dayCode("123456")
                .dayColorHex("#2563EB")
                .dayColorName("Niebieski")
                .serverTime(Instant.now())
                .build();

        when(profileCardService.getResidentCard(any(User.class))).thenReturn(card);

        mockMvc.perform(get("/api/v1/profile/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Jan"))
                .andExpect(jsonPath("$.data.lastName").value("Kowalski"))
                .andExpect(jsonPath("$.data.dormitoryName").value("DS Alfa"))
                .andExpect(jsonPath("$.data.roomNumber").value("205"))
                .andExpect(jsonPath("$.data.dayCode").value("123456"))
                .andExpect(jsonPath("$.data.dayColorHex").value("#2563EB"));

        verify(profileCardService).getResidentCard(any(User.class));
    }
}
