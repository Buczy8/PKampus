package pl.edu.pk.pkampus.modules.board;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.common.PagedResponse;
import pl.edu.pk.pkampus.modules.board.dto.CommentDto;
import pl.edu.pk.pkampus.modules.board.dto.CreateCommentRequestDto;
import pl.edu.pk.pkampus.modules.board.dto.CreatePostRequestDto;
import pl.edu.pk.pkampus.modules.board.dto.PostDto;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Board", description = "Resident community board posts and comments (FR-BOARD)")
public class PostController {

    private final PostService postService;

    @GetMapping
    @Operation(summary = "Community board feed with optional filters")
    public ResponseEntity<ApiResponse<PagedResponse<PostDto>>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) PostCategory category,
            @RequestParam(required = false) PostScope scope,
            @RequestParam(required = false, defaultValue = "ACTIVE") String status,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(postService.listFeed(user, category, scope, status, page, size)));
    }

    @PostMapping
    @Operation(summary = "Publish a community board post")
    public ResponseEntity<ApiResponse<PostDto>> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreatePostRequestDto request
    ) {
        PostDto created = postService.create(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Post published"));
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "List comments under a post")
    public ResponseEntity<ApiResponse<List<CommentDto>>> listComments(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(postService.listComments(user, id)));
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "Add a comment under a post")
    public ResponseEntity<ApiResponse<CommentDto>> addComment(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody CreateCommentRequestDto request
    ) {
        CommentDto created = postService.addComment(user, id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Comment added"));
    }

    @PatchMapping("/{id}/resolve")
    @Operation(summary = "Mark own post as resolved")
    public ResponseEntity<ApiResponse<PostDto>> resolve(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(postService.resolve(user, id), "Post marked as resolved"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete own post")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        postService.softDelete(user, id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Post deleted"));
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "Soft-delete own comment")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @AuthenticationPrincipal User user,
            @PathVariable UUID commentId
    ) {
        postService.deleteComment(user, commentId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Comment deleted"));
    }
}
