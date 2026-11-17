package com.antifraud.service.rules;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Rule parameters and the risk points each rule adds when it fires. */
@ConfigurationProperties("antifraud.rules")
public record RuleProperties(Scored blacklistedCard, Scored blacklistedMerchant) {

    public record Scored(int score) {
    }
}
