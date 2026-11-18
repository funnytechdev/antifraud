package com.antifraud.service.settings;

import com.antifraud.contracts.Verdict;

import java.time.Instant;

/**
 * Risk score thresholds, both inclusive.
 *
 * @param reviewThreshold minimal score that sends a transaction to review
 * @param blockThreshold  minimal score that blocks a transaction
 * @param updatedAt       when the thresholds were last changed
 */
public record ScoringSettings(int reviewThreshold, int blockThreshold, Instant updatedAt) {

    public ScoringSettings {
        validate(reviewThreshold, blockThreshold);
    }

    public static void validate(int reviewThreshold, int blockThreshold) {
        if (reviewThreshold < 1) {
            throw new InvalidSettingsException("reviewThreshold must be positive");
        }
        if (reviewThreshold > blockThreshold) {
            throw new InvalidSettingsException("reviewThreshold %d must not exceed blockThreshold %d"
                    .formatted(reviewThreshold, blockThreshold));
        }
    }

    public Verdict verdictFor(int riskScore) {
        if (riskScore >= blockThreshold) {
            return Verdict.BLOCK;
        }
        return riskScore >= reviewThreshold ? Verdict.REVIEW : Verdict.ALLOW;
    }
}
