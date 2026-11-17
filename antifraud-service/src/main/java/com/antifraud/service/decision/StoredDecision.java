package com.antifraud.service.decision;

import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.Verdict;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A decision as it is kept in the database, together with the transaction it was made for. */
public record StoredDecision(
        UUID transactionId,
        String cardId,
        String customerId,
        String merchantId,
        String mcc,
        BigDecimal amount,
        String currency,
        Verdict verdict,
        int riskScore,
        List<RuleHit> triggeredRules,
        Instant eventTime,
        Instant decidedAt) {
}
