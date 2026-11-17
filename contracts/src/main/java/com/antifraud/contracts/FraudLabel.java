package com.antifraud.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Ground truth for a fraudulent transaction, published by the generator to a separate topic.
 *
 * <p>Labels travel apart from transactions on purpose: the scoring path never sees them, just as
 * a real bank learns about fraud later, from chargebacks. They are only used to measure quality.
 *
 * @param scenario name of the fraud scenario that produced the transaction
 */
public record FraudLabel(UUID transactionId, String cardId, String scenario, Instant timestamp) {
}
