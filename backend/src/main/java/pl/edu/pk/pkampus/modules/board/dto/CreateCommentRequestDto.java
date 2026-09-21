package pl.edu.pk.pkampus.modules.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequestDto(
        @NotBlank @Size(max = 2000) String content
) {
}
