package pl.edu.pk.pkampus.common.exception;

import java.io.Serial;

public class SlotConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SlotConflictException(String message) {
        super(message);
    }
}
