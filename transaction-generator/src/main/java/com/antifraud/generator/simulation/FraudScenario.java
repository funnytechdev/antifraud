package com.antifraud.generator.simulation;

/** Fraud patterns the generator mixes into the honest traffic. */
public enum FraudScenario {
    /** Card testing: a burst of small purchases on one card within a couple of minutes. */
    VELOCITY_BURST,
    /** A purchase with a card that is known to be stolen. */
    COMPROMISED_CARD,
    /** An honest customer pays a merchant that is run by fraudsters. */
    FRAUD_MERCHANT
}
