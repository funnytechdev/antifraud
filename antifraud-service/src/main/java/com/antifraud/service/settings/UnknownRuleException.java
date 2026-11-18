package com.antifraud.service.settings;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class UnknownRuleException extends RuntimeException {

    public UnknownRuleException(String code) {
        super("Unknown rule: " + code);
    }
}
