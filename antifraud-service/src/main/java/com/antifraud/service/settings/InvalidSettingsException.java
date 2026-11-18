package com.antifraud.service.settings;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Settings that the service refuses to apply; the API answers with 400 and the reason. */
public class InvalidSettingsException extends ResponseStatusException {

    public InvalidSettingsException(String reason) {
        super(HttpStatus.BAD_REQUEST, reason);
    }
}
