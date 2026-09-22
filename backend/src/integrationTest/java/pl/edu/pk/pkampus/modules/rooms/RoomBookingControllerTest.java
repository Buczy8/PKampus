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
import pl.edu.pk.pkampus.modules.rooms.dto.CreateRoomBookingRequestDto;
import pl.edu.pk.pkampus.modules.rooms.dto.RoomAvailabilityDto;
import pl.edu.pk.pkampus.modules.rooms.dto.RoomBookingDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.config.JwtAuthenticationFilter;
import pl.edu.pk.pkampus.security.config.MustChangePasswordFilter;
import pl.edu.pk.pkampus.security.config.SecurityConfig;
import pl.edu.pk.pkampus.security.ratelimit.AuthRateLimitFilter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomBookingController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RoomBookingController slice tests")
class RoomBookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoomBookingService roomBookingService;

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
    @DisplayName("GET /api/v1/rooms/{id}/availability returns busy intervals")
    void availabilityReturns200() throws Exception {
        UUID roomId = UUID.randomUUID();
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(1);
        RoomAvailabilityDto availabilityDto = new RoomAvailabilityDto(roomId, List.of());

        when(roomBookingService.availability(any(), eq(roomId), eq(from), eq(to)))
                .thenReturn(availabilityDto);

        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/availability")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roomId").value(roomId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/rooms/bookings/me returns resident bookings")
    void myBookingsReturns200() throws Exception {
        UUID bookingId = UUID.randomUUID();
        RoomBookingDto bookingDto = new RoomBookingDto(
                bookingId,
                UUID.randomUUID(),
                "Salka Muzyczna",
                resident.getId(),
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now().plusHours(3),
                2,
                "Ćwiczenia",
                RoomBookingStatus.CONFIRMED,
                OffsetDateTime.now()
        );

        when(roomBookingService.listMyBookings(any())).thenReturn(List.of(bookingDto));

        mockMvc.perform(get("/api/v1/rooms/bookings/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(bookingId.toString()))
                .andExpect(jsonPath("$.data[0].roomName").value("Salka Muzyczna"));
    }

    @Test
    @DisplayName("POST /api/v1/rooms/bookings creates booking and returns 201 Created")
    void createBookingReturns201() throws Exception {
        UUID roomId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now().plusHours(1);
        OffsetDateTime end = start.plusHours(2);
        CreateRoomBookingRequestDto request = new CreateRoomBookingRequestDto(
                roomId, start, end, 3, "Projekt", true
        );

        UUID bookingId = UUID.randomUUID();
        RoomBookingDto bookingDto = new RoomBookingDto(
                bookingId,
                roomId,
                "Salka Cichej Nauki",
                resident.getId(),
                start,
                end,
                3,
                "Projekt",
                RoomBookingStatus.CONFIRMED,
                OffsetDateTime.now()
        );

        when(roomBookingService.createBooking(any(), any())).thenReturn(bookingDto);

        mockMvc.perform(post("/api/v1/rooms/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.message").value("Room booking confirmed"));
    }

    @Test
    @DisplayName("POST /api/v1/rooms/bookings fails validation when terms not accepted")
    void createBookingFailsValidation() throws Exception {
        UUID roomId = UUID.randomUUID();
        CreateRoomBookingRequestDto invalid = new CreateRoomBookingRequestDto(
                roomId,
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now().plusHours(2),
                2,
                "Projekt",
                false // not accepted!
        );

        mockMvc.perform(post("/api/v1/rooms/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/rooms/bookings/{id} cancels booking")
    void cancelBookingReturns200() throws Exception {
        UUID bookingId = UUID.randomUUID();
        RoomBookingDto bookingDto = new RoomBookingDto(
                bookingId,
                UUID.randomUUID(),
                "Salka Muzyczna",
                resident.getId(),
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now().plusHours(3),
                2,
                "Ćwiczenia",
                RoomBookingStatus.CANCELLED_USER,
                OffsetDateTime.now()
        );

        when(roomBookingService.cancelBooking(any(), eq(bookingId))).thenReturn(bookingDto);

        mockMvc.perform(delete("/api/v1/rooms/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED_USER"))
                .andExpect(jsonPath("$.message").value("Room booking cancelled"));

        verify(roomBookingService).cancelBooking(any(), eq(bookingId));
    }
}
