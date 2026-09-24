package pl.edu.pk.pkampus.modules.board;

import pl.edu.pk.pkampus.common.exception.BusinessRuleException;

import java.util.Locale;

/**
 * Feed status filter. {@code ALL} means no status restriction.
 */
public enum PostStatusFilter {
    ACTIVE,
    RESOLVED,
    ALL;

    public static PostStatusFilter from(String raw, PostStatusFilter defaultIfBlank) {
        if (raw == null || raw.isBlank()) {
            return defaultIfBlank;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("status must be ACTIVE, RESOLVED, or ALL");
        }
    }

    /**
     * Maps the filter to a {@link PostStatus} for queries, or {@code null}
     * when the filter imposes no restriction ({@code ALL}).
     */
    public PostStatus toPostStatus() {
        return this == ALL ? null : PostStatus.valueOf(name());
    }
}
