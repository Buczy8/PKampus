package pl.edu.pk.pkampus.common.exception;

import java.io.Serial;

public class MailDeliveryException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
