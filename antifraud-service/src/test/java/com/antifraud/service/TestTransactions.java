package com.antifraud.service;

import com.antifraud.contracts.TransactionEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Test data builders. */
public final class TestTransactions {

    public static final String HONEST_MERCHANT = "merch-honest";

    public static final Place MOSCOW = new Place("Moscow", 55.7558, 37.6173, "Europe/Moscow");
    /** About 40 km from the centre of Moscow. */
    public static final Place PODOLSK = new Place("Podolsk", 55.4312, 37.5454, "Europe/Moscow");
    /** About 630 km from Moscow. */
    public static final Place SAINT_PETERSBURG = new Place("Saint Petersburg", 59.9311, 30.3609, "Europe/Moscow");
    /** About 2 800 km from Moscow. */
    public static final Place NOVOSIBIRSK = new Place("Novosibirsk", 55.0084, 82.9357, "Asia/Novosibirsk");

    private TestTransactions() {
    }

    /** A card id that no other test uses, so tests do not share state in Redis. */
    public static String newCardId() {
        return "card-" + UUID.randomUUID();
    }

    public static String newMerchantId() {
        return "merch-" + UUID.randomUUID();
    }

    public static String newCustomerId() {
        return "cust-" + UUID.randomUUID();
    }

    public static TransactionEvent purchase(String cardId, Instant timestamp) {
        return purchase(cardId).at(timestamp).build();
    }

    public static TransactionEvent purchase(String cardId, String merchantId, Instant timestamp) {
        return purchase(cardId).merchant(merchantId).at(timestamp).build();
    }

    /** A grocery purchase of 1 250.50 RUB in Moscow; every field can be changed. */
    public static Builder purchase(String cardId) {
        return new Builder(cardId);
    }

    public record Place(String city, double latitude, double longitude, String timeZone) {
    }

    public static final class Builder {

        private final String cardId;
        private String customerId = "cust-test";
        private BigDecimal amount = new BigDecimal("1250.50");
        private String merchantId = HONEST_MERCHANT;
        private String mcc = "5411";
        private Place place = MOSCOW;
        private Instant timestamp = Instant.parse("2026-03-01T10:00:00Z");

        private Builder(String cardId) {
            this.cardId = cardId;
        }

        public Builder customer(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder amount(String amount) {
            this.amount = new BigDecimal(amount);
            return this;
        }

        public Builder merchant(String merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public Builder mcc(String mcc) {
            this.mcc = mcc;
            return this;
        }

        public Builder in(Place place) {
            this.place = place;
            return this;
        }

        public Builder at(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public TransactionEvent build() {
            return new TransactionEvent(UUID.randomUUID(), cardId, customerId, amount, "RUB", merchantId, mcc,
                    place.city(), place.latitude(), place.longitude(), timestamp, place.timeZone());
        }
    }
}
