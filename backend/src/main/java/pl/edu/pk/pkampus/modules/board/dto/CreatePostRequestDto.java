package pl.edu.pk.pkampus.modules.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.edu.pk.pkampus.modules.board.PostCategory;
import pl.edu.pk.pkampus.modules.board.PostScope;

public record CreatePostRequestDto(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 4000) String content,
        @NotNull PostCategory category,
        @NotNull PostScope scope
) {
}
