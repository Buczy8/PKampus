package pl.edu.pk.pkampus.modules.admin.dormrooms;

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
import pl.edu.pk.pkampus.modules.admin.dto.CreateDormRoomRequestDto;
import pl.edu.pk.pkampus.modules.admin.dto.DormRoomDto;
import pl.edu.pk.pkampus.modules.admin.dto.UpdateDormRoomRequestDto;
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

@WebMvcTest(AdminDormRoomController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminDormRoomController unit tests")
class AdminDormRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminDormRoomService adminDormRoomService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private MustChangePasswordFilter mustChangePasswordFilter;

    @MockitoBean
    private AuthRateLimitFilter authRateLimitFilter;

    @MockitoBean
    private UserRepository userRepository;

    private User admin;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        roomId = UUID.randomUUID();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listRoomsReturnsOk() throws Exception {
        when(adminDormRoomService.list(any())).thenReturn(List.of(DormRoomDto.builder()
                .id(roomId)
                .roomNumber("101")
                .floor(1)
                .capacity(2)
                .build()));

        mockMvc.perform(get("/api/v1/admin/dorm-rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].roomNumber").value("101"))
                .andExpect(jsonPath("$.data[0].floor").value(1));
    }

    @Test
    void createRoomReturnsCreated() throws Exception {
        CreateDormRoomRequestDto request = CreateDormRoomRequestDto.builder()
                .roomNumber("205")
                .floor(2)
                .capacity(2)
                .build();

        when(adminDormRoomService.create(any(), any())).thenReturn(DormRoomDto.builder()
                .id(roomId)
                .roomNumber("205")
                .floor(2)
                .capacity(2)
                .build());

        mockMvc.perform(post("/api/v1/admin/dorm-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Room created"))
                .andExpect(jsonPath("$.data.roomNumber").value("205"));
    }

    @Test
    void createRoomRejectsInvalidDto() throws Exception {
        CreateDormRoomRequestDto invalid = CreateDormRoomRequestDto.builder()
                .roomNumber("")
                .floor(-1)
                .capacity(0)
                .build();

        mockMvc.perform(post("/api/v1/admin/dorm-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRoomReturnsOk() throws Exception {
        UpdateDormRoomRequestDto request = UpdateDormRoomRequestDto.builder()
                .capacity(3)
                .build();

        when(adminDormRoomService.update(any(), eq(roomId), any())).thenReturn(DormRoomDto.builder()
                .id(roomId)
                .roomNumber("101")
                .floor(1)
                .capacity(3)
                .build());

        mockMvc.perform(patch("/api/v1/admin/dorm-rooms/" + roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.capacity").value(3));
    }

    @Test
    void updateRoomPropagatesNotFound() throws Exception {
        UpdateDormRoomRequestDto request = UpdateDormRoomRequestDto.builder()
                .capacity(3)
                .build();

        when(adminDormRoomService.update(any(), eq(roomId), any()))
                .thenThrow(new ResourceNotFoundException("Room not found"));

        mockMvc.perform(patch("/api/v1/admin/dorm-rooms/" + roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRoomPropagatesBusinessRuleException() throws Exception {
        UpdateDormRoomRequestDto request = UpdateDormRoomRequestDto.builder()
                .roomNumber("102")
                .build();

        when(adminDormRoomService.update(any(), eq(roomId), any()))
                .thenThrow(new BusinessRuleException("A room with this number already exists in the dormitory"));

        mockMvc.perform(patch("/api/v1/admin/dorm-rooms/" + roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }
}
