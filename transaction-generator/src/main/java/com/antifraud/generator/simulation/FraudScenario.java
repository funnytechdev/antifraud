package com.antifraud.generator.simulation;

/** Fraud patterns the generator mixes into the honest traffic. */
public enum FraudScenario {
    /** Card testing: a burst of small purchases on one card within a couple of minutes. */
    VELOCITY_BURST,
    /** A purchase with a card that is known to be stolen. */
    COMPROMISED_CARD,
    /** An honest customer pays a merchant that is run by fraudsters. */
    FRAUD_MERCHANT,
    /**
     * A cloned card is used in a distant city shortly after its owner paid at home. Only the purchase
     * made with the clone is fraudulent.
     */
    CLONED_CARD_TRAVEL,
    /** Someone who took over the account buys electronics or jewelry for many times the usual bill. */
    ACCOUNT_TAKEOVER,
    /**
     * A large night-time payment in a category that turns into cash easily: money transfer,
     * quasi-cash, gambling, jewelry. Only possible while it is night in some city.
     */
    NIGHT_CASH_OUT
}
