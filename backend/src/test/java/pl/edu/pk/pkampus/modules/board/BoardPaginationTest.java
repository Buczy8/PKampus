package pl.edu.pk.pkampus.modules.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BoardPagination unit tests (AAA)")
class BoardPaginationTest {

    @Test
    @DisplayName("Should create feed PageRequest with descending createdAt sort")
    void feedPageable_validInput_returnsPageRequestSortedDesc() {
        // Arrange & Act
        PageRequest pageRequest = BoardPagination.feedPageable(2, 20);

        // Assert
        assertThat(pageRequest.getPageNumber()).isEqualTo(2);
        assertThat(pageRequest.getPageSize()).isEqualTo(20);
        assertThat(pageRequest.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageRequest.getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    @DisplayName("Should create comment PageRequest with ascending createdAt sort")
    void commentPageable_validInput_returnsPageRequestSortedAsc() {
        // Arrange & Act
        PageRequest pageRequest = BoardPagination.commentPageable(0, 50);

        // Assert
        assertThat(pageRequest.getPageNumber()).isZero();
        assertThat(pageRequest.getPageSize()).isEqualTo(50);
        assertThat(pageRequest.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageRequest.getSort().getOrderFor("createdAt").isAscending()).isTrue();
    }

    @Test
    @DisplayName("Should throw BusinessRuleException on invalid page or size")
    void validate_invalidInputs_throwsBusinessRuleException() {
        // Negative page
        assertThatThrownBy(() -> BoardPagination.feedPageable(-1, 20))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("page must be greater than or equal to 0");

        // Zero size
        assertThatThrownBy(() -> BoardPagination.feedPageable(0, 0))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("size must be between 1 and 50");

        // Size > 50
        assertThatThrownBy(() -> BoardPagination.commentPageable(0, 51))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("size must be between 1 and 50");
    }
}
