package com.antifraud.service.ingest;

import com.antifraud.contracts.DecisionEvent;
import com.antifraud.contracts.TransactionEvent;
import com.antifraud.service.decision.DecisionEngine;
import com.antifraud.service.decision.DecisionRepository;
import org.springframework.stereotype.Service;

/**
 * Handles one transaction end to end: score it, store the decision, publish the decision.
 *
 * <p>Every step is safe to repeat, so a transaction redelivered by Kafka does no harm: the rules
 * recognise it by its id, the insert is skipped on conflict and the decision is published again
 * with the same key.
 */
@Service
public class TransactionProcessor {

    private final DecisionEngine engine;
    private final DecisionRepository decisions;
    private final DecisionPublisher publisher;

    public TransactionProcessor(DecisionEngine engine, DecisionRepository decisions, DecisionPublisher publisher) {
        this.engine = engine;
        this.decisions = decisions;
        this.publisher = publisher;
    }

    public DecisionEvent process(TransactionEvent transaction) {
        DecisionEvent decision = engine.decide(transaction);
        decisions.save(transaction, decision);
        publisher.publish(decision);
        return decision;
    }
}
