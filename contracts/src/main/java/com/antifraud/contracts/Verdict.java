package com.antifraud.contracts;

/** Outcome of scoring a transaction. */
public enum Verdict {
    /** Let the operation through. */
    ALLOW,
    /** Ask the customer for an additional confirmation. */
    REVIEW,
    /** Decline the operation. */
    BLOCK
}
