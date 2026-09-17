package pl.edu.pk.pkampus.security.token;

import java.time.Instant;
import java.util.UUID;

public record EmailTokenPayload(
        UUID userId,
        String email,
        Instant expiresAt
) {
}
