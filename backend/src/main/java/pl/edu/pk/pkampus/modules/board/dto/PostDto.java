package pl.edu.pk.pkampus.modules.board.dto;

import pl.edu.pk.pkampus.modules.board.PostCategory;
import pl.edu.pk.pkampus.modules.board.PostScope;
import pl.edu.pk.pkampus.modules.board.PostStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PostDto(
        UUID id,
        String title,
        String content,
        PostCategory category,
        PostScope scope,
        PostStatus status,
        String authorDisplayName,
        String authorRoomNumber,
        String authorDormitoryName,
        boolean mine,
        int commentCount,
        OffsetDateTime createdAt
) {
}
