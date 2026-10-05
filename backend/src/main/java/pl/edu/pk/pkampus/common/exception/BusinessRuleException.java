package pl.edu.pk.pkampus.common.exception;

import java.io.Serial;

public class BusinessRuleException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String message) {
        super(message);
    }
}
