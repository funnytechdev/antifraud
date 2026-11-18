package com.antifraud.service.decision;

import com.antifraud.contracts.Verdict;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * What to decide when some rules could not be evaluated because their state is unavailable.
 *
 * <p>The rules that still work are always applied; this only sets the lowest verdict for the
 * transaction:
 * <ul>
 *     <li>{@code ALLOW} (fail-open): trust the remaining rules. Customers notice nothing, but
 *     whatever only the skipped rules catch goes through.</li>
 *     <li>{@code REVIEW}: ask the customer to confirm every purchase from {@code reviewMinAmount}
 *     on; smaller ones are let through. Fraud is stopped where the losses are, at the cost of
 *     extra confirmations while the outage lasts.</li>
 *     <li>{@code BLOCK} (fail-closed): decline every purchase from {@code reviewMinAmount} on.
 *     Nothing slips through, and nobody can pay.</li>
 * </ul>
 *
 * @param fallbackVerdict lowest verdict for a transaction from {@code reviewMinAmount} on
 * @param reviewMinAmount smaller transactions are decided by the remaining rules only
 */
@ConfigurationProperties("antifraud.degradation")
public record DegradationProperties(Verdict fallbackVerdict, BigDecimal reviewMinAmount) {

    public DegradationProperties {
        if (fallbackVerdict == null) {
            throw new IllegalArgumentException("antifraud.degradation.fallback-verdict is required");
        }
        if (reviewMinAmount == null || reviewMinAmount.signum() < 0) {
            throw new IllegalArgumentException("antifraud.degradation.review-min-amount must not be negative");
        }
    }

    Verdict floorFor(BigDecimal amount) {
        return amount.compareTo(reviewMinAmount) >= 0 ? fallbackVerdict : Verdict.ALLOW;
    }
}
