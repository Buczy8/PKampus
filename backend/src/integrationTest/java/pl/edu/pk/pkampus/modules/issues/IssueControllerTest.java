package pl.edu.pk.pkampus.modules.issues;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.edu.pk.pkampus.modules.issues.dto.CreateIssueRequestDto;
import pl.edu.pk.pkampus.modules.issues.dto.IssueDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.config.JwtAuthenticationFilter;
import pl.edu.pk.pkampus.security.config.MustChangePasswordFilter;
import pl.edu.pk.pkampus.security.config.SecurityConfig;
import pl.edu.pk.pkampus.security.ratelimit.AuthRateLimitFilter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IssueController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("IssueController slice tests")
class IssueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IssueService issueService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private MustChangePasswordFilter mustChangePasswordFilter;

    @MockitoBean
    private AuthRateLimitFilter authRateLimitFilter;

    @MockitoBean
    private UserRepository userRepository;

    private User resident;
    private UUID issueId;

    @BeforeEach
    void setUp() {
        resident = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .build();
        issueId = UUID.randomUUID();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(resident, null, resident.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/issues/me returns resident's issues")
    void myIssuesReturnsList() throws Exception {
        IssueDto dto = new IssueDto(
                issueId,
                "Pokój 205",
                UUID.randomUUID(),
                null,
                IssueCategory.PLUMBING,
                IssueUrgency.URGENT,
                "Cieknie kran",
                IssueStatus.NEW,
                null,
                false,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(issueService.listMyIssues(any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/issues/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(issueId.toString()))
                .andExpect(jsonPath("$.data[0].locationLabel").value("Pokój 205"))
                .andExpect(jsonPath("$.data[0].category").value("PLUMBING"));
    }

    @Test
    @DisplayName("POST /api/v1/issues creates issue from multipart request")
    void createIssueMultipartReturnsCreated() throws Exception {
        CreateIssueRequestDto request = new CreateIssueRequestDto(
                IssueLocationType.MY_ROOM,
                null,
                IssueCategory.PLUMBING,
                IssueUrgency.URGENT,
                "Cieknie kran"
        );

        MockMultipartFile dataPart = new MockMultipartFile(
                "data",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        IssueDto created = new IssueDto(
                issueId,
                "Pokój 205",
                UUID.randomUUID(),
                null,
                IssueCategory.PLUMBING,
                IssueUrgency.URGENT,
                "Cieknie kran",
                IssueStatus.NEW,
                null,
                false,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(issueService.createIssue(any(), any(), any())).thenReturn(created);

        mockMvc.perform(multipart("/api/v1/issues")
                        .file(dataPart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Issue reported"))
                .andExpect(jsonPath("$.data.id").value(issueId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/issues/{id}/photo redirects to presigned URL")
    void photoRedirectsToPresignedUrl() throws Exception {
        when(issueService.getOwnIssuePhotoPresignedUrl(any(), eq(issueId)))
                .thenReturn("https://minio.test/issue-photo.jpg");

        mockMvc.perform(get("/api/v1/issues/" + issueId + "/photo"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://minio.test/issue-photo.jpg"))
                .andExpect(jsonPath("$.data.url").value("https://minio.test/issue-photo.jpg"));
    }
}
