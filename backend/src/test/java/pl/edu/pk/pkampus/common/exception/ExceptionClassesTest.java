package pl.edu.pk.pkampus.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Exception classes unit tests")
class ExceptionClassesTest {

    @Test
    @DisplayName("BusinessRuleException preserves message")
    void businessRuleException() {
        BusinessRuleException ex = new BusinessRuleException("Rule violated");
        assertEquals("Rule violated", ex.getMessage());
    }

    @Test
    @DisplayName("ResourceNotFoundException preserves message")
    void resourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found: 123");
        assertEquals("User not found: 123", ex.getMessage());
    }

    @Test
    @DisplayName("RateLimitExceededException preserves message and retryAfterSeconds")
    void rateLimitExceededException() {
        RateLimitExceededException ex = new RateLimitExceededException("Too many requests", 45L);
        assertEquals("Too many requests", ex.getMessage());
        assertEquals(45L, ex.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("AccountStatusException preserves message")
    void accountStatusException() {
        AccountStatusException ex = new AccountStatusException("Account is pending approval");
        assertEquals("Account is pending approval", ex.getMessage());
    }

    @Test
    @DisplayName("SlotConflictException preserves message")
    void slotConflictException() {
        SlotConflictException ex = new SlotConflictException("Time slot is overlapping");
        assertEquals("Time slot is overlapping", ex.getMessage());
    }

    @Test
    @DisplayName("EmailVerificationTokenInvalidException preserves message")
    void emailVerificationTokenInvalidException() {
        EmailVerificationTokenInvalidException ex = new EmailVerificationTokenInvalidException("Token expired");
        assertEquals("Token expired", ex.getMessage());
    }

    @Test
    @DisplayName("InvalidFileException preserves message and cause")
    void invalidFileException() {
        InvalidFileException ex = new InvalidFileException("Unsupported MIME");
        assertEquals("Unsupported MIME", ex.getMessage());

        Throwable cause = new IllegalArgumentException("Bad header");
        InvalidFileException exWithCause = new InvalidFileException("Header failed", cause);
        assertEquals("Header failed", exWithCause.getMessage());
        assertEquals(cause, exWithCause.getCause());
    }

    @Test
    @DisplayName("FileStorageException preserves message and cause")
    void fileStorageException() {
        FileStorageException ex = new FileStorageException("Bucket not accessible");
        assertEquals("Bucket not accessible", ex.getMessage());

        Throwable cause = new RuntimeException("S3 network timeout");
        FileStorageException exWithCause = new FileStorageException("S3 failed", cause);
        assertEquals("S3 failed", exWithCause.getMessage());
        assertEquals(cause, exWithCause.getCause());
    }

    @Test
    @DisplayName("InvalidTokenException preserves message")
    void invalidTokenException() {
        InvalidTokenException ex = new InvalidTokenException("Invalid JWT signature");
        assertEquals("Invalid JWT signature", ex.getMessage());
    }
}
