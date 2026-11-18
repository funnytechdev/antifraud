package com.antifraud.service.settings;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Settings that the service refuses to apply; the API answers with 400. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidSettingsException extends RuntimeException {

    public InvalidSettingsException(String message) {
        super(message);
    }
}
