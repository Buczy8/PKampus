package pl.edu.pk.pkampus.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.booking.BookingAutoCancellationService;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignment;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.dormitory.RoomRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryBooking;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingStatus;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachine;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachineRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachineStatus;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.JwtService;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("E2E: Laundry & Key Desk Lifecycle (Booking -> 2/week Limit -> Issue Key -> Return Key -> 15min Auto-cancellation)")
class LaundryAndKeyDeskE2ETest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DormitoryRepository dormitoryRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomAssignmentRepository roomAssignmentRepository;

    @Autowired
    private LaundryMachineRepository laundryMachineRepository;

    @Autowired
    private LaundryBookingRepository laundryBookingRepository;

    @Autowired
    private BookingAutoCancellationService bookingAutoCancellationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private MinioStorageService minioStorageService;

    @MockitoBean
    private EmailService emailService;

    private Dormitory dorm;
    private LaundryMachine machine1;
    private LaundryMachine machine2;
    private User resident;
    private User receptionist;

    @BeforeEach
    void setUp() {
        dorm = dormitoryRepository.save(Dormitory.builder()
                .name("DS Pralnia E2E")
                .code("PR-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Pralnicza 10")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        Room room = roomRepository.save(Room.builder()
                .dormitory(dorm)
                .roomNumber("101")
                .floor(1)
                .capacity(2)
                .build());

        machine1 = laundryMachineRepository.save(LaundryMachine.builder()
                .dormitory(dorm)
                .machineIdentifier("Pralka 1")
                .floorLocation("Parter")
                .status(LaundryMachineStatus.AVAILABLE)
                .build());

        machine2 = laundryMachineRepository.save(LaundryMachine.builder()
                .dormitory(dorm)
                .machineIdentifier("Pralka 2")
                .floorLocation("Parter")
                .status(LaundryMachineStatus.AVAILABLE)
                .build());

        resident = userRepository.save(User.builder()
                .email("res-pralnia-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Tomasz")
                .lastName("Pralnik")
                .phoneNumber("+48600700800")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .declaredRoomNumber("101")
                .build());

        roomAssignmentRepository.save(RoomAssignment.builder()
                .user(resident)
                .room(room)
                .academicYear("2025/2026")
                .isActive(true)
                .checkInDate(LocalDate.now().minusMonths(1))
                .build());

        receptionist = userRepository.save(User.builder()
                .email("portier-pralnia-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Marian")
                .lastName("Klucznik")
                .phoneNumber("+48700800900")
                .role(UserRole.RECEPTIONIST)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .build());
    }

    @Test
    @DisplayName("Complete Laundry Workflow: Browse -> Book 1 & 2 -> Limit 3 blocked -> Key Issued & Returned -> 15min Auto-cancellation")
    void completeLaundryWorkflow() throws Exception {
        String residentBearer = "Bearer " + jwtService.generateToken(resident, "101");
        String receptionistBearer = "Bearer " + jwtService.generateToken(receptionist, null);

        // Dni w obrębie dozwolonego horyzontu (do 7 dni naprzód) oraz tego samego okna kroczącego 7 dni
        LocalDate day1 = LocalDate.now(WARSAW).plusDays(1);
        LocalDate day2 = LocalDate.now(WARSAW).plusDays(2);
        LocalDate day3 = LocalDate.now(WARSAW).plusDays(3);

        // -------------------------------------------------------------
        // KROK 1: Sprawdzenie siatki slotów przez mieszkańca (UC-LAU-01)
        // -------------------------------------------------------------
        mockMvc.perform(get("/api/v1/laundry/schedule")
                        .header("Authorization", residentBearer)
                        .param("from", day1.toString())
                        .param("to", day1.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.machines.length()").value(2))
                .andExpect(jsonPath("$.data.slotDurationMinutes").value(90));

        // -------------------------------------------------------------
        // KROK 2: Rezerwacja pierwszego slotu (dzień 1, 07:00 - 08:30)
        // -------------------------------------------------------------
        OffsetDateTime start1 = day1.atTime(7, 0).atZone(WARSAW).toOffsetDateTime();
        OffsetDateTime end1 = start1.plusMinutes(90);

        MvcResult bookingResult1 = mockMvc.perform(post("/api/v1/laundry/bookings")
                        .header("Authorization", residentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(machine1.getId(), start1, end1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andReturn();

        String bookingId1Str = objectMapper.readTree(bookingResult1.getResponse().getContentAsString())
                .path("data").path("id").asText();
        UUID bookingId1 = UUID.fromString(bookingId1Str);

        // -------------------------------------------------------------
        // KROK 3: Rezerwacja drugiego slotu (dzień 2, 07:00 - 08:30)
        // -------------------------------------------------------------
        OffsetDateTime start2 = day2.atTime(7, 0).atZone(WARSAW).toOffsetDateTime();
        OffsetDateTime end2 = start2.plusMinutes(90);

        MvcResult bookingResult2 = mockMvc.perform(post("/api/v1/laundry/bookings")
                        .header("Authorization", residentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(machine1.getId(), start2, end2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andReturn();

        String bookingId2Str = objectMapper.readTree(bookingResult2.getResponse().getContentAsString())
                .path("data").path("id").asText();
        UUID bookingId2 = UUID.fromString(bookingId2Str);

        // -------------------------------------------------------------
        // KROK 4: Próba trzeciej rezerwacji w tym samym oknie kroczącym narusza limit BR-01 (max 2 aktywne/okno)
        // -------------------------------------------------------------
        OffsetDateTime start3 = day3.atTime(7, 0).atZone(WARSAW).toOffsetDateTime();
        OffsetDateTime end3 = start3.plusMinutes(90);

        mockMvc.perform(post("/api/v1/laundry/bookings")
                        .header("Authorization", residentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(machine2.getId(), start3, end3)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));

        // -------------------------------------------------------------
        // KROK 5: Portier sprawdza pulpit portiera (UC-REC-01)
        // -------------------------------------------------------------
        mockMvc.perform(get("/api/v1/receptionist/desk")
                        .header("Authorization", receptionistBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.laundry").isArray());

        // -------------------------------------------------------------
        // KROK 6: Portier wydaje klucz do pierwszej rezerwacji (CONFIRMED -> KEY_ISSUED)
        // -------------------------------------------------------------
        mockMvc.perform(post("/api/v1/receptionist/laundry/{id}/issue-key", bookingId1)
                        .header("Authorization", receptionistBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("KEY_ISSUED"));

        LaundryBooking b1 = laundryBookingRepository.findById(bookingId1).orElseThrow();
        assertThat(b1.getStatus()).isEqualTo(LaundryBookingStatus.KEY_ISSUED);

        // -------------------------------------------------------------
        // KROK 7: Mieszkaniec zwraca klucz po praniu (KEY_ISSUED -> COMPLETED, BR-09)
        // -------------------------------------------------------------
        mockMvc.perform(post("/api/v1/receptionist/laundry/{id}/return-key", bookingId1)
                        .header("Authorization", receptionistBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        b1 = laundryBookingRepository.findById(bookingId1).orElseThrow();
        assertThat(b1.getStatus()).isEqualTo(LaundryBookingStatus.COMPLETED);

        // -------------------------------------------------------------
        // KROK 8: Reguła 15 minut (BR-02 & NFR-REL-03)
        // Druga rezerwacja ma czas rozpoczęcia 20 minut temu, bez pobranego klucza.
        // -------------------------------------------------------------
        LaundryBooking b2 = laundryBookingRepository.findById(bookingId2).orElseThrow();
        Instant pastStartTime = Instant.now().minus(20, ChronoUnit.MINUTES);
        Instant pastEndTime = pastStartTime.plus(90, ChronoUnit.MINUTES);
        b2.setStartTime(pastStartTime);
        b2.setEndTime(pastEndTime);
        laundryBookingRepository.saveAndFlush(b2);

        // Uruchomienie procesu automatycznego anulowania przeterminowanych rezerwacji
        bookingAutoCancellationService.cancelAllExpiredBookings(15);

        // Weryfikacja zmiany stanu na AUTO_CANCELLED_15MIN
        b2 = laundryBookingRepository.findById(bookingId2).orElseThrow();
        assertThat(b2.getStatus()).isEqualTo(LaundryBookingStatus.AUTO_CANCELLED_15MIN);

        // -------------------------------------------------------------
        // KROK 9: Po zwolnieniu slotu (COMPLETED + AUTO_CANCELLED), limit 2 aktywnych w tygodniu
        // nie blokuje już użytkownika — może dokonać nowej rezerwacji
        // -------------------------------------------------------------
        mockMvc.perform(post("/api/v1/laundry/bookings")
                        .header("Authorization", residentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(machine2.getId(), start3, end3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    private String bookingJson(UUID machineId, OffsetDateTime start, OffsetDateTime end) throws Exception {
        return objectMapper.createObjectNode()
                .put("machineId", machineId.toString())
                .put("startTime", start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .put("endTime", end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .toString();
    }
}
