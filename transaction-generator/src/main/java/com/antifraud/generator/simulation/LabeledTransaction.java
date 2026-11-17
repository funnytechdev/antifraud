package com.antifraud.generator.simulation;

import com.antifraud.contracts.TransactionEvent;

/**
 * A generated transaction together with its ground truth.
 *
 * @param scenario fraud scenario that produced the transaction, {@code null} for an honest purchase
 */
public record LabeledTransaction(TransactionEvent event, FraudScenario scenario) {

    public boolean isFraud() {
        return scenario != null;
    }
}
