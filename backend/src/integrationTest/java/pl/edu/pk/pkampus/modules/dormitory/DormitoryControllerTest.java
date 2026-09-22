package pl.edu.pk.pkampus.modules.dormitory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.edu.pk.pkampus.modules.dormitory.dto.DormitoryDto;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.security.config.JwtAuthenticationFilter;
import pl.edu.pk.pkampus.security.config.MustChangePasswordFilter;
import pl.edu.pk.pkampus.security.config.SecurityConfig;
import pl.edu.pk.pkampus.security.ratelimit.AuthRateLimitFilter;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DormitoryController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("DormitoryController slice tests")
class DormitoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DormitoryService dormitoryService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private MustChangePasswordFilter mustChangePasswordFilter;

    @MockitoBean
    private AuthRateLimitFilter authRateLimitFilter;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /api/v1/dormitories returns 200 with list of dormitories")
    void getDormitoriesReturnsList() throws Exception {
        UUID id = UUID.randomUUID();
        when(dormitoryService.getAllDormitories()).thenReturn(List.of(
                DormitoryDto.builder()
                        .id(id)
                        .name("DS-1")
                        .code("DS1")
                        .address("ul. Skarżyńskiego 1")
                        .floorsCount(4)
                        .build()
        ));

        mockMvc.perform(get("/api/v1/dormitories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(id.toString()))
                .andExpect(jsonPath("$.data[0].name").value("DS-1"))
                .andExpect(jsonPath("$.data[0].code").value("DS1"))
                .andExpect(jsonPath("$.data[0].address").value("ul. Skarżyńskiego 1"))
                .andExpect(jsonPath("$.data[0].floorsCount").value(4));
    }

    @Test
    @DisplayName("GET /api/v1/dormitories returns 200 with empty list")
    void getDormitoriesReturnsEmptyList() throws Exception {
        when(dormitoryService.getAllDormitories()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/dormitories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
