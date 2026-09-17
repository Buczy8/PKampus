package pl.edu.pk.pkampus.service;

import java.time.Instant;
import java.util.UUID;

public record EmailTokenPayload(
        UUID userId,
        String email,
        Instant expiresAt
) {
}
