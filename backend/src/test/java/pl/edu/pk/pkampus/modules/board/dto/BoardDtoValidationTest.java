package pl.edu.pk.pkampus.modules.board.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.edu.pk.pkampus.modules.board.PostCategory;
import pl.edu.pk.pkampus.modules.board.PostScope;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Board DTO validation unit tests (AAA)")
class BoardDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("CreatePostRequestDto passes with valid fields")
    void createPostRequestDtoValid() {
        // Arrange
        CreatePostRequestDto dto = new CreatePostRequestDto(
                "Oddam lodówkę",
                "Stan bardzo dobry, odbiór osobisty w pokoju 201.",
                PostCategory.BUY_SELL,
                PostScope.DORMITORY
        );

        // Act
        Set<ConstraintViolation<CreatePostRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("CreatePostRequestDto fails when title, content, category, or scope are invalid")
    void createPostRequestDtoInvalid() {
        // Arrange
        CreatePostRequestDto dto = new CreatePostRequestDto(
                "",
                "   ",
                null,
                null
        );

        // Act
        Set<ConstraintViolation<CreatePostRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("title")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("content")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("category")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("scope")));
    }

    @Test
    @DisplayName("CreatePostRequestDto fails when title or content exceed max sizes")
    void createPostRequestDtoTooLong() {
        // Arrange
        CreatePostRequestDto dto = new CreatePostRequestDto(
                "A".repeat(151),
                "B".repeat(4001),
                PostCategory.GENERAL,
                PostScope.CAMPUS
        );

        // Act
        Set<ConstraintViolation<CreatePostRequestDto>> violations = validator.validate(dto);

        // Assert
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("title")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("content")));
    }

    @Test
    @DisplayName("CreateCommentRequestDto passes with valid content")
    void createCommentRequestDtoValid() {
        // Arrange
        CreateCommentRequestDto dto = new CreateCommentRequestDto("Mogę pomóc jutro popołudniu!");

        // Act
        Set<ConstraintViolation<CreateCommentRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("CreateCommentRequestDto fails when content is blank or exceeds 2000 characters")
    void createCommentRequestDtoInvalid() {
        // Arrange
        CreateCommentRequestDto blankDto = new CreateCommentRequestDto("   ");
        CreateCommentRequestDto tooLongDto = new CreateCommentRequestDto("C".repeat(2001));

        // Act
        Set<ConstraintViolation<CreateCommentRequestDto>> blankViolations = validator.validate(blankDto);
        Set<ConstraintViolation<CreateCommentRequestDto>> tooLongViolations = validator.validate(tooLongDto);

        // Assert
        assertFalse(blankViolations.isEmpty());
        assertFalse(tooLongViolations.isEmpty());
    }
}
