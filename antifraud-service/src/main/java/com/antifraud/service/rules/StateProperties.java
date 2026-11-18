package com.antifraud.service.rules;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * How long the per-card and per-customer state of the rules is kept in Redis.
 *
 * <p>Unlike rule parameters these are not business settings: they only bound the memory Redis uses.
 *
 * @param velocityTtl time to live of the purchase window of an idle card
 * @param locationTtl time to live of the last location of an idle card
 */
@ConfigurationProperties("antifraud.state")
public record StateProperties(Duration velocityTtl, Duration locationTtl) {
}
