package com.antifraud.service.rules;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Rule parameters and the risk points each rule adds when it fires. */
@ConfigurationProperties("antifraud.rules")
public record RuleProperties(Velocity velocity, Scored blacklistedCard, Scored blacklistedMerchant) {

    /**
     * @param threshold number of transactions within the window, the current one included, at
     *                  which the rule fires
     * @param window    length of the sliding window, measured in event time
     * @param stateTtl  how long the window of an idle card is kept in Redis
     */
    public record Velocity(int score, int threshold, Duration window, Duration stateTtl) {
    }

    public record Scored(int score) {
    }
}
