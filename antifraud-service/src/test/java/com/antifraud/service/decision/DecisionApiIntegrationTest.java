package com.antifraud.service.decision;

import com.antifraud.contracts.DecisionEvent;
import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.TransactionEvent;
import com.antifraud.contracts.Verdict;
import com.antifraud.service.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.antifraud.service.TestTransactions.newCardId;
import static com.antifraud.service.TestTransactions.purchase;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class DecisionApiIntegrationTest {

    private static final Instant EVENT_TIME = Instant.parse("2026-03-01T10:00:00Z");

    @LocalServerPort
    private int port;
    @Autowired
    private DecisionRepository decisions;

    private RestClient api;

    @BeforeEach
    void setUp() {
        api = RestClient.builder()
                .baseUrl("http://localhost:" + port + "/api/v1/decisions")
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    @Test
    void decisionIsReturnedWithRulesThatExplainIt() {
        RuleHit hit = new RuleHit("VELOCITY", 50, "10 transactions on the card within 120 seconds");
        TransactionEvent transaction = store(newCardId(), Verdict.REVIEW, EVENT_TIME, hit);

        ResponseEntity<StoredDecision> response = api.get().uri("/{id}", transaction.transactionId())
                .retrieve()
                .toEntity(StoredDecision.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StoredDecision decision = response.getBody();
        assertThat(decision.transactionId()).isEqualTo(transaction.transactionId());
        assertThat(decision.verdict()).isEqualTo(Verdict.REVIEW);
        assertThat(decision.riskScore()).isEqualTo(50);
        assertThat(decision.triggeredRules()).containsExactly(hit);
        assertThat(decision.amount()).isEqualByComparingTo(transaction.amount());
        assertThat(decision.eventTime()).isEqualTo(EVENT_TIME);
    }

    @Test
    void unknownTransactionIsNotFound() {
        ResponseEntity<Void> response = api.get().uri("/{id}", UUID.randomUUID()).retrieve().toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void decisionsAreFilteredByCardAndVerdictNewestFirst() {
        String cardId = newCardId();
        TransactionEvent allowed = store(cardId, Verdict.ALLOW, EVENT_TIME);
        TransactionEvent blockedEarlier = store(cardId, Verdict.BLOCK, EVENT_TIME.plusSeconds(1));
        TransactionEvent blockedLater = store(cardId, Verdict.BLOCK, EVENT_TIME.plusSeconds(2));
        store(newCardId(), Verdict.BLOCK, EVENT_TIME);

        assertThat(search("?cardId={cardId}", cardId)).extracting(StoredDecision::transactionId)
                .containsExactly(blockedLater.transactionId(), blockedEarlier.transactionId(), allowed.transactionId());
        assertThat(search("?cardId={cardId}&verdict=BLOCK", cardId)).extracting(StoredDecision::transactionId)
                .containsExactly(blockedLater.transactionId(), blockedEarlier.transactionId());
        assertThat(search("?cardId={cardId}&limit=1", cardId)).extracting(StoredDecision::transactionId)
                .containsExactly(blockedLater.transactionId());
    }

    @Test
    void decisionForTheSameTransactionIsStoredOnlyOnce() {
        TransactionEvent transaction = purchase(newCardId(), EVENT_TIME);
        DecisionEvent first = decision(transaction, Verdict.ALLOW, EVENT_TIME);
        DecisionEvent second = decision(transaction, Verdict.BLOCK, EVENT_TIME.plusSeconds(5));

        assertThat(decisions.save(transaction, first)).isTrue();
        assertThat(decisions.save(transaction, second)).isFalse();

        assertThat(decisions.findById(transaction.transactionId()))
                .hasValueSatisfying(stored -> assertThat(stored.verdict()).isEqualTo(Verdict.ALLOW));
    }

    private List<StoredDecision> search(String query, Object... variables) {
        return api.get().uri(query, variables)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private TransactionEvent store(String cardId, Verdict verdict, Instant decidedAt, RuleHit... hits) {
        TransactionEvent transaction = purchase(cardId, EVENT_TIME);
        decisions.save(transaction, decision(transaction, verdict, decidedAt, hits));
        return transaction;
    }

    private static DecisionEvent decision(TransactionEvent transaction, Verdict verdict, Instant decidedAt,
                                          RuleHit... hits) {
        int riskScore = List.of(hits).stream().mapToInt(RuleHit::score).sum();
        return new DecisionEvent(transaction.transactionId(), transaction.cardId(), verdict, riskScore,
                List.of(hits), transaction.timestamp(), decidedAt);
    }
}
