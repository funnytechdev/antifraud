package com.antifraud.service.ingest;

import com.antifraud.contracts.DecisionEvent;
import com.antifraud.contracts.TransactionEvent;
import com.antifraud.service.decision.DecisionEngine;
import com.antifraud.service.decision.DecisionRepository;
import com.antifraud.service.decision.StoredDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Handles one transaction end to end: score it, store the decision, publish the decision.
 *
 * <p>Kafka delivers at least once, so the same transaction may arrive again, for example after a
 * consumer restart. Protection works on two levels:
 * <ol>
 *     <li>A transaction that already has a decision is not scored again: the stored decision is
 *     published once more, unchanged. Rules that keep per-customer state never see it twice.</li>
 *     <li>If the service stopped after the rules had updated their state but before the decision
 *     was stored, the transaction is scored again. For that case every state update in Redis is
 *     idempotent by transaction id.</li>
 * </ol>
 */
@Service
public class TransactionProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransactionProcessor.class);

    private final DecisionEngine engine;
    private final DecisionRepository decisions;
    private final DecisionPublisher publisher;

    public TransactionProcessor(DecisionEngine engine, DecisionRepository decisions, DecisionPublisher publisher) {
        this.engine = engine;
        this.decisions = decisions;
        this.publisher = publisher;
    }

    public DecisionEvent process(TransactionEvent transaction) {
        Optional<StoredDecision> existing = decisions.findById(transaction.transactionId());
        if (existing.isPresent()) {
            log.info("Transaction {} was already decided, publishing the stored decision again",
                    transaction.transactionId());
            DecisionEvent decision = existing.get().toEvent();
            publisher.publish(decision);
            return decision;
        }

        DecisionEvent decision = engine.decide(transaction);
        if (!decisions.save(transaction, decision)) {
            // Another consumer stored a decision in the meantime, e.g. during a rebalance.
            decision = decisions.findById(transaction.transactionId()).orElseThrow().toEvent();
        }
        publisher.publish(decision);
        return decision;
    }
}
