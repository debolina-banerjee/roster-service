package com.pareidolia.roster_service.exception;

public class RosterGenerationException extends RuntimeException {

    public RosterGenerationException(String message) {
        super(message);
    }

    public RosterGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
