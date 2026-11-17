package com.antifraud.service.decision;

import com.antifraud.contracts.Verdict;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Risk score thresholds, both inclusive.
 *
 * @param reviewThreshold minimal score that sends a transaction to review
 * @param blockThreshold  minimal score that blocks a transaction
 */
@ConfigurationProperties("antifraud.scoring")
public record ScoringProperties(int reviewThreshold, int blockThreshold) {

    public ScoringProperties {
        if (reviewThreshold > blockThreshold) {
            throw new IllegalArgumentException("Review threshold %d must not exceed block threshold %d"
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
