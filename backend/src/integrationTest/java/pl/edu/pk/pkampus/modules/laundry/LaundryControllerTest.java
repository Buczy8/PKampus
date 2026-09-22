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
import pl.edu.pk.pkampus.modules.laundry.dto.CreateLaundryBookingRequestDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundryBookingDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundryMachineDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundryScheduleDayDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundryScheduleResponseDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.config.JwtAuthenticationFilter;
import pl.edu.pk.pkampus.security.config.MustChangePasswordFilter;
import pl.edu.pk.pkampus.security.config.SecurityConfig;
import pl.edu.pk.pkampus.security.ratelimit.AuthRateLimitFilter;

import java.time.LocalDate;
import java.time.LocalTime;
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

@WebMvcTest(LaundryController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("LaundryController slice tests")
class LaundryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LaundryService laundryService;

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
    @DisplayName("GET /api/v1/laundry/schedule returns schedule")
    void getScheduleReturnsResponse() throws Exception {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(1);
        LaundryScheduleResponseDto responseDto = new LaundryScheduleResponseDto(
                LocalTime.of(7, 0),
                LocalTime.of(23, 0),
                180,
                List.of(new LaundryMachineDto(UUID.randomUUID(), "Pralka 1", "Parter", LaundryMachineStatus.AVAILABLE)),
                List.of(new LaundryScheduleDayDto(from, List.of()))
        );

        when(laundryService.getSchedule(any(), eq(from), eq(to))).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/laundry/schedule")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slotDurationMinutes").value(180))
                .andExpect(jsonPath("$.data.machines[0].identifier").value("Pralka 1"));
    }

    @Test
    @DisplayName("GET /api/v1/laundry/bookings/me returns resident bookings")
    void myBookingsReturnsList() throws Exception {
        UUID bookingId = UUID.randomUUID();
        LaundryBookingDto bookingDto = new LaundryBookingDto(
                bookingId,
                UUID.randomUUID(),
                "Pralka 1",
                resident.getId(),
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now().plusHours(4),
                LaundryBookingStatus.CONFIRMED,
                OffsetDateTime.now()
        );

        when(laundryService.listMyBookings(any())).thenReturn(List.of(bookingDto));

        mockMvc.perform(get("/api/v1/laundry/bookings/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(bookingId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("POST /api/v1/laundry/bookings books slot and returns 201 Created")
    void createBookingReturns201() throws Exception {
        UUID machineId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now().plusHours(1);
        OffsetDateTime end = start.plusHours(3);
        CreateLaundryBookingRequestDto request = new CreateLaundryBookingRequestDto(machineId, start, end);

        UUID bookingId = UUID.randomUUID();
        LaundryBookingDto bookingDto = new LaundryBookingDto(
                bookingId,
                machineId,
                "Pralka 1",
                resident.getId(),
                start,
                end,
                LaundryBookingStatus.CONFIRMED,
                OffsetDateTime.now()
        );

        when(laundryService.bookSlot(any(), any())).thenReturn(bookingDto);

        mockMvc.perform(post("/api/v1/laundry/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.message").value("Laundry booking confirmed"));
    }

    @Test
    @DisplayName("POST /api/v1/laundry/bookings fails validation with empty body")
    void createBookingFailsValidation() throws Exception {
        mockMvc.perform(post("/api/v1/laundry/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/laundry/bookings/{id} cancels booking")
    void cancelBookingReturns200() throws Exception {
        UUID bookingId = UUID.randomUUID();
        LaundryBookingDto bookingDto = new LaundryBookingDto(
                bookingId,
                UUID.randomUUID(),
                "Pralka 1",
                resident.getId(),
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now().plusHours(4),
                LaundryBookingStatus.CANCELLED_USER,
                OffsetDateTime.now()
        );

        when(laundryService.cancelBooking(any(), eq(bookingId))).thenReturn(bookingDto);

        mockMvc.perform(delete("/api/v1/laundry/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED_USER"))
                .andExpect(jsonPath("$.message").value("Laundry booking cancelled"));

        verify(laundryService).cancelBooking(any(), eq(bookingId));
    }
}
