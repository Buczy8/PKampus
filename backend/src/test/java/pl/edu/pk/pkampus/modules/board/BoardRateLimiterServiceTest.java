package pl.edu.pk.pkampus.modules.board;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.edu.pk.pkampus.common.exception.RateLimitExceededException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BoardRateLimiterService unit tests (AAA)")
class BoardRateLimiterServiceTest {

    private BoardRateLimiterService rateLimiterService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        // Arrange
        rateLimiterService = new BoardRateLimiterService();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should allow post creation within configured capacity")
    void checkPostRateLimit_withinCapacity_shouldSucceed() {
        // Arrange
        rateLimiterService.setPostLimits(3, 10);

        // Act & Assert
        rateLimiterService.checkPostRateLimit(userId);
        rateLimiterService.checkPostRateLimit(userId);
        rateLimiterService.checkPostRateLimit(userId);
    }

    @Test
    @DisplayName("Should throw RateLimitExceededException when post capacity exceeded")
    void checkPostRateLimit_exceedsCapacity_shouldThrowRateLimitExceededException() {
        // Arrange
        rateLimiterService.setPostLimits(2, 10);
        rateLimiterService.checkPostRateLimit(userId);
        rateLimiterService.checkPostRateLimit(userId);

        // Act & Assert
        assertThatThrownBy(() -> rateLimiterService.checkPostRateLimit(userId))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("You are posting too frequently")
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isPositive();
                });
    }

    @Test
    @DisplayName("Should allow comment creation within configured capacity")
    void checkCommentRateLimit_withinCapacity_shouldSucceed() {
        // Arrange
        rateLimiterService.setCommentLimits(2, 1);

        // Act & Assert
        rateLimiterService.checkCommentRateLimit(userId);
        rateLimiterService.checkCommentRateLimit(userId);
    }

    @Test
    @DisplayName("Should throw RateLimitExceededException when comment capacity exceeded")
    void checkCommentRateLimit_exceedsCapacity_shouldThrowRateLimitExceededException() {
        // Arrange
        rateLimiterService.setCommentLimits(1, 1);
        rateLimiterService.checkCommentRateLimit(userId);

        // Act & Assert
        assertThatThrownBy(() -> rateLimiterService.checkCommentRateLimit(userId))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("You are commenting too frequently")
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isPositive();
                });
    }

    @Test
    @DisplayName("Should allow action again after bucket reset")
    void reset_shouldClearBucketsForUser() {
        // Arrange
        rateLimiterService.setPostLimits(1, 10);
        rateLimiterService.checkPostRateLimit(userId);

        assertThatThrownBy(() -> rateLimiterService.checkPostRateLimit(userId))
                .isInstanceOf(RateLimitExceededException.class);

        // Act
        rateLimiterService.reset(userId);

        // Assert
        rateLimiterService.checkPostRateLimit(userId);
    }
}
