package pl.edu.pk.pkampus.common.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GONE)
public class EmailVerificationTokenInvalidException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EmailVerificationTokenInvalidException(String message) {
        super(message);
    }
}
