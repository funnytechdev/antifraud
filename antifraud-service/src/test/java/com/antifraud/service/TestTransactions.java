package com.antifraud.service;

import com.antifraud.contracts.TransactionEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Test data builders. */
public final class TestTransactions {

    public static final String HONEST_MERCHANT = "merch-honest";

    private TestTransactions() {
    }

    /** A card id that no other test uses, so tests do not share state in Redis. */
    public static String newCardId() {
        return "card-" + UUID.randomUUID();
    }

    public static String newMerchantId() {
        return "merch-" + UUID.randomUUID();
    }

    public static TransactionEvent purchase(String cardId, Instant timestamp) {
        return purchase(cardId, HONEST_MERCHANT, timestamp);
    }

    public static TransactionEvent purchase(String cardId, String merchantId, Instant timestamp) {
        return new TransactionEvent(
                UUID.randomUUID(),
                cardId,
                "cust-test",
                new BigDecimal("1250.50"),
                "RUB",
                merchantId,
                "5411",
                "Moscow",
                55.7558,
                37.6173,
                timestamp);
    }
}
