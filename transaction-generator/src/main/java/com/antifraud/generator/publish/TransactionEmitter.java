package com.antifraud.generator.publish;

import com.antifraud.contracts.FraudLabel;
import com.antifraud.contracts.TransactionEvent;
import com.antifraud.generator.GeneratorProperties;
import com.antifraud.generator.simulation.LabeledTransaction;
import com.antifraud.generator.simulation.TransactionSimulator;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drives the simulator at the configured rate and publishes its output to Kafka.
 *
 * <p>Transactions are keyed by card id, so all purchases of a card go to the same partition and
 * keep their order. Fraud labels go to their own topic.
 */
@Component
public class TransactionEmitter {

    private static final Logger log = LoggerFactory.getLogger(TransactionEmitter.class);
    private static final Duration REPORT_INTERVAL = Duration.ofSeconds(10);

    private final TransactionSimulator simulator;
    private final KafkaTemplate<String, Object> kafka;
    private final GeneratorProperties properties;
    private final Clock clock;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "tx-emitter"));

    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong fraud = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    /** Purchases owed to the stream; keeps the fractional part between ticks. */
    private double budget;

    public TransactionEmitter(TransactionSimulator simulator, KafkaTemplate<String, Object> kafka,
                              GeneratorProperties properties, Clock clock) {
        this.simulator = simulator;
        this.kafka = kafka;
        this.properties = properties;
        this.clock = clock;
    }

    public void start() {
        long tickMillis = properties.tick().toMillis();
        scheduler.scheduleAtFixedRate(this::tick, 0, tickMillis, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::report, REPORT_INTERVAL.toSeconds(), REPORT_INTERVAL.toSeconds(),
                TimeUnit.SECONDS);
        log.info("Emitting {} transactions per second to topic '{}'",
                properties.ratePerSecond(), properties.topics().transactions());
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    private void tick() {
        try {
            budget += properties.ratePerSecond() * properties.tick().toMillis() / 1000.0;
            int count = (int) budget;
            budget -= count;
            for (LabeledTransaction transaction : simulator.next(clock.instant(), count)) {
                publish(transaction);
            }
        } catch (RuntimeException e) {
            // An exception escaping a scheduled task would silently cancel all further ticks.
            log.error("Failed to emit transactions", e);
        }
    }

    private void publish(LabeledTransaction transaction) {
        TransactionEvent event = transaction.event();
        kafka.send(properties.topics().transactions(), event.cardId(), event)
                .whenComplete((result, error) -> (error == null ? sent : failed).incrementAndGet());
        if (transaction.isFraud()) {
            fraud.incrementAndGet();
            kafka.send(properties.topics().labels(), event.transactionId().toString(),
                    new FraudLabel(event.transactionId(), event.cardId(), transaction.scenario().name(),
                            event.timestamp()));
        }
    }

    private void report() {
        log.info("Transactions sent: {}, of them fraudulent: {}, failed to send: {}",
                sent.get(), fraud.get(), failed.get());
    }
}
