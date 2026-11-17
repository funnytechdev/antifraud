package com.antifraud.service.ingest;

import com.antifraud.contracts.DecisionEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Publishes decisions to Kafka, keyed by card id like the transactions they belong to. */
@Component
public class DecisionPublisher {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, Object> kafka;
    private final TopicsProperties topics;

    public DecisionPublisher(KafkaTemplate<String, Object> kafka, TopicsProperties topics) {
        this.kafka = kafka;
        this.topics = topics;
    }

    /**
     * Sends the decision and waits for the broker to acknowledge it.
     *
     * <p>The wait matters for delivery guarantees: the consumer offset must not move past a
     * transaction until its decision is safely in Kafka. If the send fails, the exception makes
     * the listener retry the transaction.
     */
    public void publish(DecisionEvent decision) {
        try {
            kafka.send(topics.decisions(), decision.cardId(), decision)
                    .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing the decision for transaction "
                    + decision.transactionId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish the decision for transaction "
                    + decision.transactionId(), e);
        }
    }
}
