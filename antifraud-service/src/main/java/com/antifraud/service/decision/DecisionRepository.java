package com.antifraud.service.decision;

import com.antifraud.contracts.DecisionEvent;
import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.TransactionEvent;
import com.antifraud.contracts.Verdict;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DecisionRepository {

    private static final TypeReference<List<RuleHit>> RULE_HITS = new TypeReference<>() {
    };

    private static final String SELECT = """
            SELECT transaction_id, card_id, customer_id, merchant_id, mcc, amount, currency,
                   verdict, risk_score, triggered_rules, event_time, decided_at
            FROM decisions
            """;

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public DecisionRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Stores the decision unless one already exists for the transaction.
     *
     * @return {@code false} if the transaction has been decided before
     */
    public boolean save(TransactionEvent transaction, DecisionEvent decision) {
        int inserted = jdbc.sql("""
                        INSERT INTO decisions (transaction_id, card_id, customer_id, merchant_id, mcc, amount,
                                               currency, verdict, risk_score, triggered_rules, event_time, decided_at)
                        VALUES (:transactionId, :cardId, :customerId, :merchantId, :mcc, :amount,
                                :currency, :verdict, :riskScore, CAST(:triggeredRules AS jsonb), :eventTime, :decidedAt)
                        ON CONFLICT (transaction_id) DO NOTHING
                        """)
                .param("transactionId", transaction.transactionId())
                .param("cardId", transaction.cardId())
                .param("customerId", transaction.customerId())
                .param("merchantId", transaction.merchantId())
                .param("mcc", transaction.mcc())
                .param("amount", transaction.amount())
                .param("currency", transaction.currency())
                .param("verdict", decision.verdict().name())
                .param("riskScore", decision.riskScore())
                .param("triggeredRules", json.writeValueAsString(decision.triggeredRules()))
                .param("eventTime", utc(decision.eventTime()))
                .param("decidedAt", utc(decision.decidedAt()))
                .update();
        return inserted == 1;
    }

    public Optional<StoredDecision> findById(UUID transactionId) {
        return jdbc.sql(SELECT + "WHERE transaction_id = :transactionId")
                .param("transactionId", transactionId)
                .query(this::mapRow)
                .optional();
    }

    /**
     * Returns the latest decisions, newest first.
     *
     * @param cardId  only decisions of this card, or {@code null} for any card
     * @param verdict only decisions with this verdict, or {@code null} for any verdict
     */
    public List<StoredDecision> findLatest(String cardId, Verdict verdict, int limit) {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE TRUE");
        Map<String, Object> params = new HashMap<>();
        if (cardId != null) {
            sql.append(" AND card_id = :cardId");
            params.put("cardId", cardId);
        }
        if (verdict != null) {
            sql.append(" AND verdict = :verdict");
            params.put("verdict", verdict.name());
        }
        sql.append(" ORDER BY decided_at DESC LIMIT :limit");
        params.put("limit", limit);
        return jdbc.sql(sql.toString()).params(params).query(this::mapRow).list();
    }

    private StoredDecision mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new StoredDecision(
                rs.getObject("transaction_id", UUID.class),
                rs.getString("card_id"),
                rs.getString("customer_id"),
                rs.getString("merchant_id"),
                rs.getString("mcc"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                Verdict.valueOf(rs.getString("verdict")),
                rs.getInt("risk_score"),
                json.readValue(rs.getString("triggered_rules"), RULE_HITS),
                rs.getObject("event_time", OffsetDateTime.class).toInstant(),
                rs.getObject("decided_at", OffsetDateTime.class).toInstant());
    }

    /** The PostgreSQL driver maps {@code timestamptz} to {@link OffsetDateTime}, not {@link Instant}. */
    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
