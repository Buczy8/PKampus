package pl.edu.pk.pkampus.modules.rooms;

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
import pl.edu.pk.pkampus.modules.sanctions.Sanction;
import pl.edu.pk.pkampus.modules.sanctions.SanctionRepository;
import pl.edu.pk.pkampus.modules.sanctions.SanctionType;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.JwtService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Thematic room bookings API integration tests")
class RoomBookingIntegrationTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DormitoryRepository dormitoryRepository;

    @Autowired
    private ThematicRoomRepository thematicRoomRepository;

    @Autowired
    private RoomBookingRepository roomBookingRepository;

    @Autowired
    private SanctionRepository sanctionRepository;

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
    private ThematicRoom room1;
    private ThematicRoom roomOtherDorm;
    private ThematicRoom chillout;
    private User resident;
    private User resident2;
    private User dormAdmin;

    @BeforeEach
    void setUp() {
        dorm1 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Rooms Book 1")
                .code("RB-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Salka 1")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        dorm2 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Rooms Book 2")
                .code("RC-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Salka 2")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        room1 = thematicRoomRepository.save(ThematicRoom.builder()
                .dormitory(dorm1)
                .name("Funzone")
                .maxCapacity(10)
                .openingTime(LocalTime.of(6, 0))
                .closingTime(LocalTime.of(23, 30))
                .spansMidnight(false)
                .maxDurationHours(4)
                .status(ThematicRoomStatus.AVAILABLE)
                .build());

        chillout = thematicRoomRepository.save(ThematicRoom.builder()
                .dormitory(dorm1)
                .name("Chillout")
                .maxCapacity(30)
                .openingTime(LocalTime.of(14, 0))
                .closingTime(LocalTime.of(2, 0))
                .spansMidnight(true)
                .maxDurationHours(12)
                .status(ThematicRoomStatus.AVAILABLE)
                .build());

        roomOtherDorm = thematicRoomRepository.save(ThematicRoom.builder()
                .dormitory(dorm2)
                .name("Other Funzone")
                .maxCapacity(10)
                .openingTime(LocalTime.of(6, 0))
                .closingTime(LocalTime.of(23, 30))
                .spansMidnight(false)
                .maxDurationHours(4)
                .status(ThematicRoomStatus.AVAILABLE)
                .build());

        resident = saveResident(dorm1, "101");
        resident2 = saveResident(dorm1, "102");
        dormAdmin = userRepository.save(User.builder()
                .email("room-ads-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Ads")
                .lastName("Admin")
                .phoneNumber("+48999999999")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .build());
    }

    @Test
    @DisplayName("Resident can create booking, list me, and see availability")
    void createListAndAvailability() throws Exception {
        LocalDate day = LocalDate.now(WARSAW).plusDays(1);
        ZonedDateTime start = day.atTime(10, 0).atZone(WARSAW);
        ZonedDateTime end = day.atTime(12, 0).atZone(WARSAW);

        mockMvc.perform(post("/api/v1/rooms/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(room1.getId(), start, end, 4, "Spotkanie koła")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.roomName").value("Funzone"))
                .andExpect(jsonPath("$.data.participantsCount").value(4));

        mockMvc.perform(get("/api/v1/rooms/bookings/me")
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(get("/api/v1/rooms/" + room1.getId() + "/availability")
                        .param("from", day.toString())
                        .param("to", day.toString())
                        .header("Authorization", bearer(resident2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.busy", hasSize(1)));
    }

    @Test
    @DisplayName("ROOM_BAN blocks booking with 403")
    void roomBanForbidden() throws Exception {
        sanctionRepository.save(Sanction.builder()
                .user(resident)
                .dormitory(dorm1)
                .sanctionType(SanctionType.ROOM_BAN)
                .reason("Test ban")
                .startDate(LocalDate.now(WARSAW).minusDays(1))
                .endDate(LocalDate.now(WARSAW).plusMonths(1))
                .active(true)
                .issuedBy(dormAdmin)
                .build());

        LocalDate day = LocalDate.now(WARSAW).plusDays(1);
        ZonedDateTime start = day.atTime(10, 0).atZone(WARSAW);
        ZonedDateTime end = day.atTime(11, 0).atZone(WARSAW);

        mockMvc.perform(post("/api/v1/rooms/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(room1.getId(), start, end, 2, "Test")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Participants over capacity returns 422")
    void capacityExceeded() throws Exception {
        LocalDate day = LocalDate.now(WARSAW).plusDays(1);
        ZonedDateTime start = day.atTime(10, 0).atZone(WARSAW);
        ZonedDateTime end = day.atTime(11, 0).atZone(WARSAW);

        mockMvc.perform(post("/api/v1/rooms/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(room1.getId(), start, end, 99, "Za dużo osób")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Outside opening hours returns 422")
    void outsideHours() throws Exception {
        LocalDate day = LocalDate.now(WARSAW).plusDays(1);
        ZonedDateTime start = day.atTime(4, 0).atZone(WARSAW);
        ZonedDateTime end = day.atTime(5, 0).atZone(WARSAW);

        mockMvc.perform(post("/api/v1/rooms/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(room1.getId(), start, end, 2, "Za wcześnie")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Overlap returns 409")
    void overlapConflict() throws Exception {
        LocalDate day = LocalDate.now(WARSAW).plusDays(2);
        ZonedDateTime start = day.atTime(15, 0).atZone(WARSAW);
        ZonedDateTime end = day.atTime(17, 0).atZone(WARSAW);

        mockMvc.perform(post("/api/v1/rooms/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(room1.getId(), start, end, 2, "Pierwszy")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/rooms/bookings")
                        .header("Authorization", bearer(resident2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(room1.getId(),
                                day.atTime(16, 0).atZone(WARSAW),
                                day.atTime(18, 0).atZone(WARSAW),
                                2, "Kolizja")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Chillout spans midnight is allowed")
    void chilloutSpansMidnight() throws Exception {
        LocalDate day = LocalDate.now(WARSAW).plusDays(1);
        ZonedDateTime start = day.atTime(22, 0).atZone(WARSAW);
        ZonedDateTime end = day.plusDays(1).atTime(1, 0).atZone(WARSAW);

        mockMvc.perform(post("/api/v1/rooms/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(chillout.getId(), start, end, 5, "Impreza")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roomName").value("Chillout"));
    }

    @Test
    @DisplayName("Second active booking same day returns 422")
    void secondActiveSameDayRejected() throws Exception {
        LocalDate day = LocalDate.now(WARSAW).plusDays(4);
        mockMvc.perform(post("/api/v1/rooms/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(room1.getId(),
                                day.atTime(9, 0).atZone(WARSAW),
                                day.atTime(11, 0).atZone(WARSAW),
                                2, "Rano")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/rooms/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(room1.getId(),
                                day.atTime(16, 0).atZone(WARSAW),
                                day.atTime(19, 0).atZone(WARSAW),
                                2, "Wieczór")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Second booking same day allowed after first has ended")
    void secondSameDayAllowedAfterFirstEnded() throws Exception {
        LocalDate day = LocalDate.now(WARSAW);
        Instant pastStart = day.atTime(6, 0).atZone(WARSAW).toInstant();
        Instant pastEnd = day.atTime(7, 0).atZone(WARSAW).toInstant();
        // Only meaningful if those hours are already in the past today
        assumeTrue(pastEnd.isBefore(Instant.now()), "needs wall-clock past morning window");

        roomBookingRepository.save(RoomBooking.builder()
                .room(room1)
                .user(resident)
                .startTime(pastStart)
                .endTime(pastEnd)
                .participantsCount(2)
                .purpose("Wcześniejsza")
                .status(RoomBookingStatus.CONFIRMED)
                .termsAccepted(true)
                .build());

        ZonedDateTime laterStart = day.atTime(16, 0).atZone(WARSAW);
        ZonedDateTime laterEnd = day.atTime(18, 0).atZone(WARSAW);
        assumeTrue(laterStart.toInstant().isAfter(Instant.now()), "needs future evening slot");

        mockMvc.perform(post("/api/v1/rooms/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(room1.getId(), laterStart, laterEnd, 2, "Po południu")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Cancel own booking before start")
    void cancelBeforeStart() throws Exception {
        LocalDate day = LocalDate.now(WARSAW).plusDays(3);
        ZonedDateTime start = day.atTime(10, 0).atZone(WARSAW);
        ZonedDateTime end = day.atTime(12, 0).atZone(WARSAW);

        MvcResult created = mockMvc.perform(post("/api/v1/rooms/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(room1.getId(), start, end, 2, "Do anulowania")))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(delete("/api/v1/rooms/bookings/" + id)
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED_USER"));

        assertThat(roomBookingRepository.findById(UUID.fromString(id)))
                .get()
                .extracting(RoomBooking::getStatus)
                .isEqualTo(RoomBookingStatus.CANCELLED_USER);
    }

    @Test
    @DisplayName("Room from another dormitory returns 404")
    void otherDormRoomNotFound() throws Exception {
        LocalDate day = LocalDate.now(WARSAW).plusDays(1);
        ZonedDateTime start = day.atTime(10, 0).atZone(WARSAW);
        ZonedDateTime end = day.atTime(11, 0).atZone(WARSAW);

        mockMvc.perform(post("/api/v1/rooms/bookings")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(roomOtherDorm.getId(), start, end, 2, "Obcy DS")))
                .andExpect(status().isNotFound());
    }

    private User saveResident(Dormitory dorm, String room) {
        return userRepository.save(User.builder()
                .email("room-res-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Test")
                .lastName("Resident")
                .phoneNumber("+48111111111")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .declaredRoomNumber(room)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private String bookingJson(
            UUID roomId,
            ZonedDateTime start,
            ZonedDateTime end,
            int participants,
            String purpose
    ) {
        return """
                {
                  "roomId": "%s",
                  "startTime": "%s",
                  "endTime": "%s",
                  "participantsCount": %d,
                  "purpose": "%s",
                  "termsAccepted": true
                }
                """.formatted(
                roomId,
                start.toOffsetDateTime().format(ISO_OFFSET),
                end.toOffsetDateTime().format(ISO_OFFSET),
                participants,
                purpose
        );
    }
}
