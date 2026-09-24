package pl.edu.pk.pkampus.modules.board;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;

/**
 * Factory and validator for Board feed and comment pagination requests.
 */
final class BoardPagination {

    public static final int MAX_FEED_PAGE_SIZE = 50;
    public static final int MAX_COMMENT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_NUMBER = 500;

    private BoardPagination() {
    }

    public static PageRequest feedPageable(int page, int size) {
        validate(page, size, MAX_FEED_PAGE_SIZE);
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public static PageRequest commentPageable(int page, int size) {
        validate(page, size, MAX_COMMENT_PAGE_SIZE);
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
    }

    private static void validate(int page, int size, int maxSize) {
        if (page < 0) {
            throw new BusinessRuleException("page must be greater than or equal to 0");
        }
        if (page > MAX_PAGE_NUMBER) {
            throw new BusinessRuleException("page must be less than or equal to " + MAX_PAGE_NUMBER);
        }
        if (size < 1 || size > maxSize) {
            throw new BusinessRuleException("size must be between 1 and " + maxSize);
        }
    }
}
