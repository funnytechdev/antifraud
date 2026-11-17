package com.antifraud.service.ingest;

import com.antifraud.contracts.DecisionEvent;
import com.antifraud.contracts.TransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Entry point of the scoring pipeline.
 *
 * <p>Transactions are partitioned by card id and a partition is read by one consumer thread at a
 * time, so the events of a card are processed strictly one after another.
 */
@Component
public class TransactionListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionListener.class);

    private final TransactionProcessor processor;

    public TransactionListener(TransactionProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = "${antifraud.topics.transactions}")
    public void onTransaction(TransactionEvent transaction) {
        DecisionEvent decision = processor.process(transaction);
        log.debug("Transaction {} of card {}: {} with risk score {}", decision.transactionId(),
                decision.cardId(), decision.verdict(), decision.riskScore());
    }
}
