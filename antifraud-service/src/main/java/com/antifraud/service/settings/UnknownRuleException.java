package com.antifraud.service.settings;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class UnknownRuleException extends ResponseStatusException {

    public UnknownRuleException(String code) {
        super(HttpStatus.NOT_FOUND, "Unknown rule: " + code);
    }
}
