package pl.edu.pk.pkampus.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Framework-agnostic paginated envelope for list endpoints.
 * Keeps Spring Data {@link Page} inside service/repository layers instead of leaking it to the API.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PagedResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        boolean last = totalPages == 0 || page >= totalPages - 1;
        return new PagedResponse<>(List.copyOf(content), page, size, totalElements, totalPages, last);
    }

    public static <T> PagedResponse<T> from(Page<T> page) {
        return of(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
