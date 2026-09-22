package pl.edu.pk.pkampus.modules.rooms;

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
import pl.edu.pk.pkampus.modules.rooms.dto.CreateThematicRoomRequestDto;
import pl.edu.pk.pkampus.modules.rooms.dto.ThematicRoomDto;
import pl.edu.pk.pkampus.modules.rooms.dto.UpdateThematicRoomRequestDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.config.JwtAuthenticationFilter;
import pl.edu.pk.pkampus.security.config.MustChangePasswordFilter;
import pl.edu.pk.pkampus.security.config.SecurityConfig;
import pl.edu.pk.pkampus.security.ratelimit.AuthRateLimitFilter;

import java.time.LocalTime;
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

@WebMvcTest(AdminThematicRoomController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminThematicRoomController slice tests")
class AdminThematicRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ThematicRoomService thematicRoomService;

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
    @DisplayName("GET /api/v1/admin/thematic-rooms returns room list")
    void listReturnsRooms() throws Exception {
        UUID roomId = UUID.randomUUID();
        ThematicRoomDto dto = ThematicRoomDto.builder()
                .id(roomId)
                .dormitoryId(UUID.randomUUID())
                .name("Salka Gier")
                .maxCapacity(10)
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(ThematicRoomStatus.AVAILABLE)
                .build();

        when(thematicRoomService.listForAdmin(any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/admin/thematic-rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(roomId.toString()))
                .andExpect(jsonPath("$.data[0].name").value("Salka Gier"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/thematic-rooms creates room and returns 201 Created")
    void createReturns201() throws Exception {
        CreateThematicRoomRequestDto request = CreateThematicRoomRequestDto.builder()
                .name("Salka Muzyczna")
                .maxCapacity(6)
                .openingTime(LocalTime.of(10, 0))
                .closingTime(LocalTime.of(20, 0))
                .maxDurationHours(3)
                .build();

        UUID roomId = UUID.randomUUID();
        ThematicRoomDto dto = ThematicRoomDto.builder()
                .id(roomId)
                .name("Salka Muzyczna")
                .maxCapacity(6)
                .openingTime(LocalTime.of(10, 0))
                .closingTime(LocalTime.of(20, 0))
                .status(ThematicRoomStatus.AVAILABLE)
                .build();

        when(thematicRoomService.create(any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/admin/thematic-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(roomId.toString()))
                .andExpect(jsonPath("$.message").value("Thematic room created"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/thematic-rooms fails validation with blank name")
    void createFailsValidation() throws Exception {
        CreateThematicRoomRequestDto invalid = CreateThematicRoomRequestDto.builder()
                .name("   ")
                .maxCapacity(6)
                .openingTime(LocalTime.of(10, 0))
                .closingTime(LocalTime.of(20, 0))
                .maxDurationHours(3)
                .build();

        mockMvc.perform(post("/api/v1/admin/thematic-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/thematic-rooms/{id} updates room")
    void updateReturns200() throws Exception {
        UUID roomId = UUID.randomUUID();
        UpdateThematicRoomRequestDto request = UpdateThematicRoomRequestDto.builder()
                .name("Nowa Salka")
                .maxCapacity(15)
                .build();

        ThematicRoomDto dto = ThematicRoomDto.builder()
                .id(roomId)
                .name("Nowa Salka")
                .maxCapacity(15)
                .status(ThematicRoomStatus.AVAILABLE)
                .build();

        when(thematicRoomService.update(any(), eq(roomId), any())).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/admin/thematic-rooms/" + roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Nowa Salka"));

        verify(thematicRoomService).update(any(), eq(roomId), any());
    }
}
