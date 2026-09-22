package pl.edu.pk.pkampus.modules.laundry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.JwtService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Laundry bookings API integration tests")
class LaundryBookingIntegrationTest {

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
    private LaundryMachineRepository laundryMachineRepository;

    @Autowired
    private LaundryBookingRepository laundryBookingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private MinioStorageService minioStorageService;

    @MockitoBean
    private EmailService emailService;

    private Dormitory dorm1;
    private Dormitory dorm2;
    private LaundryMachine machine1;
    private LaundryMachine machineOtherDorm;
    private User resident;
    private User resident2;
    private User receptionist;

    @BeforeEach
    void setUp() {
        dorm1 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Laundry 1")
                .code("L1-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Pralnicza 1")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        dorm2 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Laundry 2")
                .code("L2-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Pralnicza 2")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        machine1 = laundryMachineRepository.save(LaundryMachine.builder()
                .dormitory(dorm1)
                .machineIdentifier("Washer A")
                .floorLocation("Ground")
                .status(LaundryMachineStatus.AVAILABLE)
                .build());

        laundryMachineRepository.save(LaundryMachine.builder()
                .dormitory(dorm1)
                .machineIdentifier("Washer B")
                .floorLocation("Ground")
                .status(LaundryMachineStatus.AVAILABLE)
                .build());

        machineOtherDorm = laundryMachineRepository.save(LaundryMachine.builder()
                .dormitory(dorm2)
                .machineIdentifier("Washer X")
                .floorLocation("Basement")
                .status(LaundryMachineStatus.AVAILABLE)
                .build());

        resident = userRepository.save(User.builder()
                .email("laundry-res-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password1!"))
                .firstName("Jan")
                .lastName("Kowalski")
                .phoneNumber("+48111111111")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .declaredRoomNumber("101")
                .build());

        resident2 = userRepository.save(User.builder()
                .email("laundry-res2-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password1!"))
                .firstName("Anna")
                .lastName("Nowak")
                .phoneNumber("+48222222222")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .declaredRoomNumber("102")
                .build());

        receptionist = userRepository.save(User.builder()
                .email("laundry-port-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password1!"))
                .firstName("Portier")
                .lastName("Test")
                .phoneNumber("+48333333333")
                .role(UserRole.RECEPTIONIST)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .build());
    }

    @Test
    @DisplayName("GET schedule returns FREE slots for dorm machines")
    void scheduleReturnsFreeSlots() throws Exception {
        LocalDate from = LocalDate.now(WARSAW).plusDays(1);
        LocalDate to = from;

        mockMvc.perform(get("/api/v1/laundry/schedule")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slotDurationMinutes").value(90))
                .andExpect(jsonPath("$.data.machines.length()").value(2))
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0].slots[0].state").value("FREE"));
    }

    @Test
    @DisplayName("POST booking creates CONFIRMED; second resident on same slot gets 409")
    void bookThenConflict() throws Exception {
        OffsetDateTime start = slotStart(1, LocalTime.of(7, 0));
        OffsetDateTime end = start.plusMinutes(90);

        mockMvc.perform(post("/api/v1/laundry/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(machine1.getId(), start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.machineId").value(machine1.getId().toString()));

        mockMvc.perform(post("/api/v1/laundry/bookings")
                        .header("Authorization", bearer(resident2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(machine1.getId(), start, end)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Second booking on the same Warsaw day returns 422")
    void sameDayLimitReturns422() throws Exception {
        OffsetDateTime s1 = slotStart(1, LocalTime.of(7, 0));
        OffsetDateTime s2 = slotStart(1, LocalTime.of(8, 30));

        bookOk(resident, machine1.getId(), s1, s1.plusMinutes(90));

        mockMvc.perform(post("/api/v1/laundry/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(machine1.getId(), s2, s2.plusMinutes(90))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("per calendar day")));
    }

    @Test
    @DisplayName("Third active booking within 7 days from reservation date returns 422")
    void rollingSevenDayLimitReturns422() throws Exception {
        OffsetDateTime s1 = slotStart(1, LocalTime.of(7, 0));
        OffsetDateTime s2 = slotStart(2, LocalTime.of(7, 0));
        OffsetDateTime s3 = slotStart(3, LocalTime.of(7, 0));

        bookOk(resident, machine1.getId(), s1, s1.plusMinutes(90));
        bookOk(resident, machine1.getId(), s2, s2.plusMinutes(90));

        mockMvc.perform(post("/api/v1/laundry/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(machine1.getId(), s3, s3.plusMinutes(90))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("within 7 days")));
    }

    @Test
    @DisplayName("Booking beyond 7-day horizon returns 422")
    void horizonReturns422() throws Exception {
        OffsetDateTime start = slotStart(8, LocalTime.of(7, 0));
        OffsetDateTime end = start.plusMinutes(90);

        mockMvc.perform(post("/api/v1/laundry/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(machine1.getId(), start, end)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("7 days")));
    }

    @Test
    @DisplayName("Cancel before start sets CANCELLED_USER; cancel after start returns 400")
    void cancelBeforeAndAfterStart() throws Exception {
        OffsetDateTime start = slotStart(1, LocalTime.of(10, 0));
        OffsetDateTime end = start.plusMinutes(90);

        MvcResult created = mockMvc.perform(post("/api/v1/laundry/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(machine1.getId(), start, end)))
                .andExpect(status().isCreated())
                .andReturn();

        String bookingId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(delete("/api/v1/laundry/bookings/" + bookingId)
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED_USER"));

        LaundryBooking past = laundryBookingRepository.save(LaundryBooking.builder()
                .machine(machine1)
                .user(resident)
                .startTime(Instant.now().minus(2, ChronoUnit.HOURS))
                .endTime(Instant.now().minus(30, ChronoUnit.MINUTES))
                .status(LaundryBookingStatus.CONFIRMED)
                .build());

        mockMvc.perform(delete("/api/v1/laundry/bookings/" + past.getId())
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("after the slot has started")));
    }

    @Test
    @DisplayName("Booking machine from another dormitory returns 404")
    void otherDormMachineNotFound() throws Exception {
        OffsetDateTime start = slotStart(1, LocalTime.of(7, 0));
        OffsetDateTime end = start.plusMinutes(90);

        mockMvc.perform(post("/api/v1/laundry/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(machineOtherDorm.getId(), start, end)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Non-RESIDENT receives 403")
    void receptionistForbidden() throws Exception {
        LocalDate from = LocalDate.now(WARSAW).plusDays(1);

        mockMvc.perform(get("/api/v1/laundry/schedule")
                        .param("from", from.toString())
                        .param("to", from.toString())
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET bookings/me returns active bookings")
    void myBookings() throws Exception {
        OffsetDateTime start = slotStart(1, LocalTime.of(14, 30));
        bookOk(resident, machine1.getId(), start, start.plusMinutes(90));

        mockMvc.perform(get("/api/v1/laundry/bookings/me")
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("Booking OUT_OF_ORDER machine returns 422 Unprocessable Entity")
    void bookOutOfOrderMachineReturns422() throws Exception {
        machine1.setStatus(LaundryMachineStatus.OUT_OF_ORDER);
        laundryMachineRepository.save(machine1);

        OffsetDateTime start = slotStart(1, LocalTime.of(7, 0));
        OffsetDateTime end = start.plusMinutes(90);

        mockMvc.perform(post("/api/v1/laundry/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(machine1.getId(), start, end)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("out of order")));
    }

    @Test
    @DisplayName("Booking with mismatched end time duration returns 422")
    void bookMismatchedDurationReturns422() throws Exception {
        OffsetDateTime start = slotStart(1, LocalTime.of(7, 0));
        OffsetDateTime end = start.plusMinutes(60); // 60 min instead of 90 min!

        mockMvc.perform(post("/api/v1/laundry/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(machine1.getId(), start, end)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("endTime must equal startTime plus slot duration")));
    }

    @Test
    @DisplayName("Unauthenticated request to laundry API returns 401")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/laundry/bookings/me"))
                .andExpect(status().isUnauthorized());
    }

    private void bookOk(User user, UUID machineId, OffsetDateTime start, OffsetDateTime end) throws Exception {
        mockMvc.perform(post("/api/v1/laundry/bookings")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(machineId, start, end)))
                .andExpect(status().isCreated());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user, user.getDeclaredRoomNumber());
    }

    private static OffsetDateTime slotStart(int daysAhead, LocalTime time) {
        LocalDate date = LocalDate.now(WARSAW).plusDays(daysAhead);
        return date.atTime(time).atZone(WARSAW).toOffsetDateTime();
    }

    private String bookingJson(UUID machineId, OffsetDateTime start, OffsetDateTime end) throws Exception {
        return objectMapper.createObjectNode()
                .put("machineId", machineId.toString())
                .put("startTime", start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .put("endTime", end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .toString();
    }
}
