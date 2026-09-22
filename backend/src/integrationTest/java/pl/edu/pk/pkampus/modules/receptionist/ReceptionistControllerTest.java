package pl.edu.pk.pkampus.modules.receptionist;

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
import pl.edu.pk.pkampus.modules.issues.IssueStatus;
import pl.edu.pk.pkampus.modules.profile.ProfileCardService;
import pl.edu.pk.pkampus.modules.profile.dto.CardDayDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.DeskLaundryBookingDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.DeskRoomBookingDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.MachineBreakdownRequestDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.MachineBreakdownResponseDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.ReceptionistDeskDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.RoomMaintenanceRequestDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.RoomMaintenanceResponseDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.StaffIssueDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.UpdateIssueStatusRequestDto;
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

@WebMvcTest(ReceptionistController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ReceptionistController slice tests")
class ReceptionistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReceptionistService receptionistService;

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

    private User receptionist;

    @BeforeEach
    void setUp() {
        receptionist = User.builder()
                .id(UUID.randomUUID())
                .email("portier@pk.edu.pl")
                .role(UserRole.RECEPTIONIST)
                .status(UserStatus.ACTIVE)
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(receptionist, null, receptionist.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/receptionist/desk returns desk snapshot")
    void getDeskReturns200() throws Exception {
        ReceptionistDeskDto deskDto = new ReceptionistDeskDto(List.of(), List.of(), 0, List.of());
        when(receptionistService.getDesk(any())).thenReturn(deskDto);

        mockMvc.perform(get("/api/v1/receptionist/desk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.openIssuesCount").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/receptionist/card-day returns card day token")
    void getCardDayReturns200() throws Exception {
        CardDayDto cardDayDto = CardDayDto.builder()
                .dayCode("123ABC")
                .dayColorHex("#2563EB")
                .dayColorName("Niebieski")
                .validDate("2026-09-22")
                .serverTime(Instant.now())
                .build();

        when(profileCardService.getCardDay()).thenReturn(cardDayDto);

        mockMvc.perform(get("/api/v1/receptionist/card-day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dayCode").value("123ABC"))
                .andExpect(jsonPath("$.data.dayColorName").value("Niebieski"));
    }

    @Test
    @DisplayName("POST /api/v1/receptionist/laundry/machines/{id}/breakdown marks machine out of order")
    void machineBreakdownReturns200() throws Exception {
        UUID machineId = UUID.randomUUID();
        MachineBreakdownRequestDto request = new MachineBreakdownRequestDto("Awaria grzałki");
        MachineBreakdownResponseDto response = new MachineBreakdownResponseDto(machineId, 2, UUID.randomUUID());

        when(receptionistService.reportMachineBreakdown(any(), eq(machineId), eq("Awaria grzałki")))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/receptionist/laundry/machines/" + machineId + "/breakdown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cancelledCount").value(2))
                .andExpect(jsonPath("$.message").value("Laundry machine marked out of order"));
    }

    @Test
    @DisplayName("POST /api/v1/receptionist/laundry/machines/{id}/breakdown fails validation with blank reason")
    void machineBreakdownFailsValidation() throws Exception {
        UUID machineId = UUID.randomUUID();
        MachineBreakdownRequestDto request = new MachineBreakdownRequestDto("   ");

        mockMvc.perform(post("/api/v1/receptionist/laundry/machines/" + machineId + "/breakdown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/receptionist/rooms/{id}/maintenance marks room maintenance")
    void roomMaintenanceReturns200() throws Exception {
        UUID roomId = UUID.randomUUID();
        RoomMaintenanceRequestDto request = new RoomMaintenanceRequestDto("Naprawa klimatyzacji");
        RoomMaintenanceResponseDto response = new RoomMaintenanceResponseDto(roomId, 1, UUID.randomUUID());

        when(receptionistService.reportRoomMaintenance(any(), eq(roomId), eq("Naprawa klimatyzacji")))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/receptionist/rooms/" + roomId + "/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cancelledCount").value(1))
                .andExpect(jsonPath("$.message").value("Thematic room marked under maintenance"));
    }

    @Test
    @DisplayName("POST /api/v1/receptionist/rooms/{id}/maintenance fails validation on blank reason")
    void roomMaintenanceFailsValidation() throws Exception {
        UUID roomId = UUID.randomUUID();
        RoomMaintenanceRequestDto request = new RoomMaintenanceRequestDto("");

        mockMvc.perform(post("/api/v1/receptionist/rooms/" + roomId + "/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/v1/receptionist/issues/{id}/status updates status")
    void updateIssueStatusReturns200() throws Exception {
        UUID issueId = UUID.randomUUID();
        UpdateIssueStatusRequestDto request = new UpdateIssueStatusRequestDto(
                IssueStatus.IN_PROGRESS,
                "Przydzielono technika"
        );

        when(receptionistService.updateIssueStatus(any(), eq(issueId), eq(IssueStatus.IN_PROGRESS), eq("Przydzielono technika")))
                .thenReturn(null);

        mockMvc.perform(patch("/api/v1/receptionist/issues/" + issueId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Issue status updated"));
    }

    @Test
    @DisplayName("PATCH /api/v1/receptionist/issues/{id}/status fails validation when status is null")
    void updateIssueStatusFailsValidation() throws Exception {
        UUID issueId = UUID.randomUUID();
        UpdateIssueStatusRequestDto request = new UpdateIssueStatusRequestDto(null, "Notatki");

        mockMvc.perform(patch("/api/v1/receptionist/issues/" + issueId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/receptionist/laundry/{id}/issue-key issues key")
    void issueLaundryKeyReturns200() throws Exception {
        UUID bookingId = UUID.randomUUID();

        when(receptionistService.issueLaundryKey(any(), eq(bookingId))).thenReturn(null);

        mockMvc.perform(post("/api/v1/receptionist/laundry/" + bookingId + "/issue-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Laundry key issued"));

        verify(receptionistService).issueLaundryKey(any(), eq(bookingId));
    }

    @Test
    @DisplayName("POST /api/v1/receptionist/rooms/{id}/return-key returns room key")
    void returnRoomKeyReturns200() throws Exception {
        UUID bookingId = UUID.randomUUID();

        when(receptionistService.returnRoomKey(any(), eq(bookingId))).thenReturn(null);

        mockMvc.perform(post("/api/v1/receptionist/rooms/" + bookingId + "/return-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Room key returned"));

        verify(receptionistService).returnRoomKey(any(), eq(bookingId));
    }
}
