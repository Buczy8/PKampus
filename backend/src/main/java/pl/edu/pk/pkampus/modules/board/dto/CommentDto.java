package pl.edu.pk.pkampus.modules.board.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CommentDto(
        UUID id,
        UUID postId,
        String content,
        String authorDisplayName,
        String authorRoomNumber,
        String authorDormitoryName,
        boolean mine,
        OffsetDateTime createdAt
) {
}
