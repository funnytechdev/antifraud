package com.antifraud.service.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * What happens when a transaction cannot be processed.
 *
 * <p>By default Spring Kafka retries a failed record ten times and then skips it. For this service
 * skipping means a transaction left without a decision, so a failure of PostgreSQL or of the
 * Kafka producer is retried with a growing pause until the dependency is back. The partition
 * waits, and the consumer lag shows the delay.
 *
 * <p>A message that cannot be deserialized is never going to succeed. Spring Kafka classifies
 * such errors as not retryable, so they are logged and skipped right away.
 */
@Configuration(proxyBeanMethods = false)
class KafkaErrorHandlingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfiguration.class);

    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(200, 2.0);
        backOff.setMaxInterval(10_000);
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, error) -> log.error("Skipping record {}-{}@{}: {}", record.topic(), record.partition(),
                        record.offset(), error.getMessage()),
                backOff);
        handler.setRetryListeners((record, error, attempt) -> log.warn(
                "Attempt {} to process record {}-{}@{} failed: {}", attempt, record.topic(), record.partition(),
                record.offset(), error.getMessage()));
        return handler;
    }
}
