package com.antifraud.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A card purchase as it arrives in the {@code transactions} topic.
 *
 * <p>The record key in Kafka is {@link #cardId()}, so all events of one card land in the same
 * partition and are processed in order.
 *
 * @param transactionId unique id of the operation, used for idempotent processing
 * @param cardId        card token (never the real card number)
 * @param timestamp     moment of the purchase (event time), not the moment of processing
 */
public record TransactionEvent(
        UUID transactionId,
        String cardId,
        String customerId,
        BigDecimal amount,
        String currency,
        String merchantId,
        String mcc,
        String city,
        double latitude,
        double longitude,
        Instant timestamp) {

    public TransactionEvent {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(cardId, "cardId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(mcc, "mcc");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
