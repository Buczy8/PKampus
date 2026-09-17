package pl.edu.pk.pkampus.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccountStatusException extends RuntimeException {

    public AccountStatusException(String message) {
        super(message);
    }
}
