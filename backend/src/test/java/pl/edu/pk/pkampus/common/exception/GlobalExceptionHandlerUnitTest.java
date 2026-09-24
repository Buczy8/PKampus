package pl.edu.pk.pkampus.common.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.modules.board.Post;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GlobalExceptionHandler unit tests")
class GlobalExceptionHandlerUnitTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleValidationExceptions should return 400 Bad Request with field errors map")
    void handleValidationExceptions() throws NoSuchMethodException {
        record DummyDto(String email, String password) {}
        Method method = getClass().getDeclaredMethod("setUp");
        MethodParameter parameter = new MethodParameter(method, -1);

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new DummyDto("", ""), "dummyDto");
        bindingResult.addError(new FieldError("dummyDto", "email", "Email is required"));
        bindingResult.addError(new FieldError("dummyDto", "password", "Password must be at least 8 characters"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiResponse<Map<String, String>>> response = exceptionHandler.handleValidationExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("Validation error", response.getBody().message());
        assertNotNull(response.getBody().data());
        assertEquals("Email is required", response.getBody().data().get("email"));
        assertEquals("Password must be at least 8 characters", response.getBody().data().get("password"));
    }

    @Test
    @DisplayName("handleBadCredentials should return 401 Unauthorized with generic message")
    void handleBadCredentials() {
        BadCredentialsException ex = new BadCredentialsException("Internal auth failure");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleBadCredentials(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("Invalid email or password", response.getBody().message());
    }

    @Test
    @DisplayName("handleInvalidToken should return 401 Unauthorized with token message")
    void handleInvalidToken() {
        InvalidTokenException ex = new InvalidTokenException("JWT signature has expired");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleInvalidToken(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("JWT signature has expired", response.getBody().message());
    }

    @Test
    @DisplayName("handleAccessDenied should return 403 Forbidden with standard message")
    void handleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Access Denied");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("Access denied: insufficient permissions", response.getBody().message());
    }

    @Test
    @DisplayName("handleIllegalArgument should return 400 Bad Request")
    void handleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid date range provided");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("Invalid date range provided", response.getBody().message());
    }

    @Test
    @DisplayName("handleBusinessRule should return 422 Unprocessable Entity")
    void handleBusinessRule() {
        BusinessRuleException ex = new BusinessRuleException("Maximum 2 laundry bookings allowed per week");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleBusinessRule(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("Maximum 2 laundry bookings allowed per week", response.getBody().message());
    }

    @Test
    @DisplayName("handleSlotConflict should return 409 Conflict")
    void handleSlotConflict() {
        SlotConflictException ex = new SlotConflictException("The selected slot is already booked");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleSlotConflict(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("The selected slot is already booked", response.getBody().message());
    }

    @Test
    @DisplayName("handleDataIntegrity should detect Postgres 23P01 exclusion violation and return friendly conflict message")
    void handleDataIntegrityExclusion() {
        SQLException sqlEx = new SQLException("exclusion violation", "23P01");
        ConstraintViolationException hibernateEx = new ConstraintViolationException("Overlapping range", sqlEx, "chk_laundry_no_overlap");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Database constraint violated", hibernateEx);

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("Slot was just taken by another resident", response.getBody().message());
    }

    @Test
    @DisplayName("handleDataIntegrity should return standard message for non-exclusion constraint violation")
    void handleDataIntegrityGeneral() {
        SQLException sqlEx = new SQLException("foreign key violation", "23503");
        ConstraintViolationException hibernateEx = new ConstraintViolationException("FK failed", sqlEx, "fk_user");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("FK failed", hibernateEx);

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("Data integrity constraint violated", response.getBody().message());
    }

    @Test
    @DisplayName("handleEmailVerificationTokenInvalid should return 410 Gone")
    void handleEmailVerificationTokenInvalid() {
        EmailVerificationTokenInvalidException ex = new EmailVerificationTokenInvalidException("Activation token has expired");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleEmailVerificationTokenInvalid(ex);

        assertEquals(HttpStatus.GONE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("Activation token has expired", response.getBody().message());
    }

    @Test
    @DisplayName("handleInvalidFile should return 400 Bad Request")
    void handleInvalidFile() {
        InvalidFileException ex = new InvalidFileException("File size exceeds 5 MB");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleInvalidFile(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("File size exceeds 5 MB", response.getBody().message());
    }

    @Test
    @DisplayName("handleFileStorage should return 500 Internal Server Error")
    void handleFileStorage() {
        FileStorageException ex = new FileStorageException("MinIO S3 connection lost");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleFileStorage(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("MinIO S3 connection lost", response.getBody().message());
    }

    @Test
    @DisplayName("handleMailDelivery should return 500 Internal Server Error")
    void handleMailDelivery() {
        MailDeliveryException ex = new MailDeliveryException("SMTP connection failed", new RuntimeException("timeout"));
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleMailDelivery(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("SMTP connection failed", response.getBody().message());
    }

    @Test
    @DisplayName("handleAccountStatus should return 403 Forbidden")
    void handleAccountStatus() {
        AccountStatusException ex = new AccountStatusException("Account is pending approval");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAccountStatus(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("Account is pending approval", response.getBody().message());
    }

    @Test
    @DisplayName("handleResourceNotFound should return 404 Not Found")
    void handleResourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Laundry machine #99 not found");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleResourceNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("Laundry machine #99 not found", response.getBody().message());
    }

    @Test
    @DisplayName("handleRateLimitExceeded should return 429 with Retry-After and rate limit headers")
    void handleRateLimitExceeded() {
        RateLimitExceededException ex = new RateLimitExceededException("Too many login attempts", 30L);
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleRateLimitExceeded(ex);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("30", response.getHeaders().getFirst("Retry-After"));
        assertEquals("30", response.getHeaders().getFirst("X-Rate-Limit-Retry-After-Seconds"));
        assertEquals("0", response.getHeaders().getFirst("X-Rate-Limit-Remaining"));
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("Too many login attempts", response.getBody().message());
    }

    @Test
    @DisplayName("handleGeneralException should return 500 Internal Server Error with error detail")
    void handleGeneralException() {
        NullPointerException ex = new NullPointerException("Variable was unexpectedly null");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleGeneralException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertTrue(response.getBody().message().contains("Variable was unexpectedly null"));
    }

    @Test
    @DisplayName("handleOptimisticLock should return 409 Conflict for both exception hierarchies")
    void handleOptimisticLock() {
        ResponseEntity<ApiResponse<Void>> spring = exceptionHandler.handleOptimisticLock(
                new org.springframework.orm.ObjectOptimisticLockingFailureException(Post.class, "id"));
        ResponseEntity<ApiResponse<Void>> jakarta = exceptionHandler.handleOptimisticLock(
                new jakarta.persistence.OptimisticLockException("stale post"));

        for (ResponseEntity<ApiResponse<Void>> response : new ResponseEntity[]{spring, jakarta}) {
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertNotNull(response.getBody());
            assertFalse(response.getBody().success());
            assertEquals("Resource was modified concurrently, please retry", response.getBody().message());
        }
    }
}
