package com.antifraud.service.rules;

/** The store that keeps the state of the rules cannot be reached right now. */
public class StateUnavailableException extends RuntimeException {

    public StateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
