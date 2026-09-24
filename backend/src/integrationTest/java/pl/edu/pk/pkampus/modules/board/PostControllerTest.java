package pl.edu.pk.pkampus.modules.board;

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
import pl.edu.pk.pkampus.common.PagedResponse;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.board.dto.CommentDto;
import pl.edu.pk.pkampus.modules.board.dto.CreateCommentRequestDto;
import pl.edu.pk.pkampus.modules.board.dto.CreatePostRequestDto;
import pl.edu.pk.pkampus.modules.board.dto.PostDto;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PostController slice tests")
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private MustChangePasswordFilter mustChangePasswordFilter;

    @MockitoBean
    private AuthRateLimitFilter authRateLimitFilter;

    @MockitoBean
    private UserRepository userRepository;

    private User resident;
    private UUID postId;

    @BeforeEach
    void setUp() {
        resident = User.builder()
                .id(UUID.randomUUID())
                .email("resident@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .build();
        postId = UUID.randomUUID();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(resident, null, resident.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/posts returns feed list")
    void listReturnsFeed() throws Exception {
        PostDto postDto = new PostDto(
                postId,
                "Pożyczę wiertarkę",
                "Na weekend",
                PostCategory.BORROW_HELP,
                PostScope.DORMITORY,
                PostStatus.ACTIVE,
                "Jan Kowalski",
                "101",
                "DS1",
                true,
                2,
                OffsetDateTime.now()
        );

        when(postService.listFeed(any(), eq(PostCategory.BORROW_HELP), eq(PostScope.DORMITORY), eq("ACTIVE"), eq(0), eq(20)))
                .thenReturn(PagedResponse.of(List.of(postDto), 0, 20, 1));

        mockMvc.perform(get("/api/v1/posts")
                        .param("category", "BORROW_HELP")
                        .param("scope", "DORMITORY")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(postId.toString()))
                .andExpect(jsonPath("$.data.content[0].title").value("Pożyczę wiertarkę"))
                .andExpect(jsonPath("$.data.content[0].commentCount").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/posts/{id} returns single post when found")
    void getByIdReturnsPost() throws Exception {
        PostDto postDto = new PostDto(
                postId,
                "Pożyczę wiertarkę",
                "Na weekend",
                PostCategory.BORROW_HELP,
                PostScope.DORMITORY,
                PostStatus.ACTIVE,
                "Jan Kowalski",
                "101",
                "DS1",
                true,
                2,
                OffsetDateTime.now()
        );

        when(postService.getById(any(), eq(postId))).thenReturn(postDto);

        mockMvc.perform(get("/api/v1/posts/{id}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(postId.toString()))
                .andExpect(jsonPath("$.data.title").value("Pożyczę wiertarkę"))
                .andExpect(jsonPath("$.data.commentCount").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/posts/{id} returns 404 when post not found")
    void getByIdNotFoundReturns404() throws Exception {
        when(postService.getById(any(), eq(postId))).thenThrow(new ResourceNotFoundException("Post not found"));

        mockMvc.perform(get("/api/v1/posts/{id}", postId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Post not found"));
    }

    @Test
    @DisplayName("POST /api/v1/posts returns 201 on valid body")
    void createReturnsCreated() throws Exception {
        CreatePostRequestDto request = new CreatePostRequestDto(
                "Kupię lodówkę",
                "Małą do pokoju",
                PostCategory.BUY_SELL,
                PostScope.CAMPUS
        );

        PostDto created = new PostDto(
                postId,
                "Kupię lodówkę",
                "Małą do pokoju",
                PostCategory.BUY_SELL,
                PostScope.CAMPUS,
                PostStatus.ACTIVE,
                "Jan Kowalski",
                null,
                "DS1",
                true,
                0,
                OffsetDateTime.now()
        );

        when(postService.create(any(), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Post published"))
                .andExpect(jsonPath("$.data.title").value("Kupię lodówkę"));
    }

    @Test
    @DisplayName("POST /api/v1/posts returns 400 on invalid body")
    void createRejectsInvalidBody() throws Exception {
        CreatePostRequestDto invalid = new CreatePostRequestDto(
                "",
                "",
                null,
                null
        );

        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/posts/{id}/comments returns comments list")
    void listCommentsReturnsOk() throws Exception {
        UUID commentId = UUID.randomUUID();
        CommentDto commentDto = new CommentDto(
                commentId,
                postId,
                "Mam do sprzedania!",
                "Anna Nowak",
                "202",
                "DS1",
                false,
                OffsetDateTime.now()
        );

        when(postService.listComments(any(), eq(postId), eq(0), eq(50)))
                .thenReturn(PagedResponse.of(List.of(commentDto), 0, 50, 1));

        mockMvc.perform(get("/api/v1/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(commentId.toString()))
                .andExpect(jsonPath("$.data.content[0].content").value("Mam do sprzedania!"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/posts/{id}/comments returns 201 on valid comment")
    void addCommentReturnsCreated() throws Exception {
        UUID commentId = UUID.randomUUID();
        CreateCommentRequestDto request = new CreateCommentRequestDto("Mogę pomóc!");
        CommentDto created = new CommentDto(
                commentId,
                postId,
                "Mogę pomóc!",
                "Jan Kowalski",
                "101",
                "DS1",
                true,
                OffsetDateTime.now()
        );

        when(postService.addComment(any(), eq(postId), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Comment added"))
                .andExpect(jsonPath("$.data.content").value("Mogę pomóc!"));
    }

    @Test
    @DisplayName("POST /api/v1/posts/{id}/comments returns 400 on blank content")
    void addCommentRejectsBlankContent() throws Exception {
        CreateCommentRequestDto blank = new CreateCommentRequestDto("   ");

        mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blank)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/v1/posts/{id}/resolve returns resolved post")
    void resolveReturnsOk() throws Exception {
        PostDto resolved = new PostDto(
                postId,
                "Pożyczę wiertarkę",
                "Na weekend",
                PostCategory.BORROW_HELP,
                PostScope.DORMITORY,
                PostStatus.RESOLVED,
                "Jan Kowalski",
                "101",
                "DS1",
                true,
                1,
                OffsetDateTime.now()
        );

        when(postService.resolve(any(), eq(postId))).thenReturn(resolved);

        mockMvc.perform(patch("/api/v1/posts/" + postId + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Post marked as resolved"))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/posts/{id}/resolve propagates BusinessRuleException")
    void resolvePropagatesConflict() throws Exception {
        when(postService.resolve(any(), eq(postId)))
                .thenThrow(new BusinessRuleException("Only ACTIVE posts can be marked as resolved"));

        mockMvc.perform(patch("/api/v1/posts/" + postId + "/resolve"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("DELETE /api/v1/posts/{id} returns 200 on soft delete")
    void deleteReturnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Post deleted"));

        verify(postService).softDelete(any(), eq(postId));
    }

    @Test
    @DisplayName("DELETE /api/v1/posts/comments/{id} returns 200 on soft delete")
    void deleteCommentReturnsOk() throws Exception {
        UUID commentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/posts/comments/" + commentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Comment deleted"));

        verify(postService).deleteComment(any(), eq(commentId));
    }

    @Test
    @DisplayName("DELETE /api/v1/posts/comments/{id} propagates ResourceNotFoundException")
    void deleteCommentPropagatesNotFound() throws Exception {
        UUID commentId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Comment not found"))
                .when(postService).deleteComment(any(), eq(commentId));

        mockMvc.perform(delete("/api/v1/posts/comments/" + commentId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/v1/posts/{id} propagates ResourceNotFoundException")
    void deletePropagatesNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Post not found"))
                .when(postService).softDelete(any(), eq(postId));

        mockMvc.perform(delete("/api/v1/posts/" + postId))
                .andExpect(status().isNotFound());
    }
}
