package pl.edu.pk.pkampus.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GONE)
public class EmailVerificationTokenInvalidException extends RuntimeException {

    public EmailVerificationTokenInvalidException(String message) {
        super(message);
    }
}
