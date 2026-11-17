package com.antifraud.generator;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param seed             seed of the simulated world and of the purchase stream
 * @param ratePerSecond    new purchases per second, fraud bursts come on top of that
 * @param tick             how often a portion of purchases is emitted
 * @param fraudProbability chance that a new purchase starts a fraud scenario
 */
@ConfigurationProperties("generator")
public record GeneratorProperties(
        long seed,
        int ratePerSecond,
        Duration tick,
        double fraudProbability,
        WorldSettings world,
        TopicSettings topics) {

    public record WorldSettings(int customers, int merchants, int compromisedCards, int fraudMerchants) {
    }

    public record TopicSettings(String transactions, String labels, int partitions) {
    }
}
