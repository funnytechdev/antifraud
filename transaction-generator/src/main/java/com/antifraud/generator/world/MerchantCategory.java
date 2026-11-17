package com.antifraud.generator.world;

/**
 * Merchant categories with their MCC codes.
 *
 * <p>{@code amountFactor} scales a customer's typical purchase for the category (a jewelry store
 * bill is much larger than a bus ticket); {@code weight} is how common such merchants are.
 */
public enum MerchantCategory {
    GROCERY("5411", 1.0, 30),
    RESTAURANT("5812", 1.5, 15),
    TRANSPORT("4111", 0.1, 15),
    MISCELLANEOUS("5999", 1.5, 10),
    FUEL("5541", 2.0, 8),
    PHARMACY("5912", 0.8, 8),
    CLOTHING("5651", 4.0, 6),
    ELECTRONICS("5732", 12.0, 3),
    MONEY_TRANSFER("4829", 5.0, 2),
    JEWELRY("5944", 20.0, 1),
    QUASI_CASH("6051", 8.0, 1),
    GAMBLING("7995", 3.0, 1);

    private final String mcc;
    private final double amountFactor;
    private final int weight;

    MerchantCategory(String mcc, double amountFactor, int weight) {
        this.mcc = mcc;
        this.amountFactor = amountFactor;
        this.weight = weight;
    }

    public String mcc() {
        return mcc;
    }

    public double amountFactor() {
        return amountFactor;
    }

    public int weight() {
        return weight;
    }
}
