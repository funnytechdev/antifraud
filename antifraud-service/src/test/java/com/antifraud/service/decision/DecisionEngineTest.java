package com.antifraud.service.decision;

import com.antifraud.contracts.DecisionEvent;
import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.TransactionEvent;
import com.antifraud.contracts.Verdict;
import com.antifraud.service.TestTransactions;
import com.antifraud.service.rules.FraudRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DecisionEngineTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:05Z");
    private static final ScoringProperties SCORING = new ScoringProperties(40, 80);

    private final TransactionEvent transaction =
            TestTransactions.purchase("card-1", Instant.parse("2026-03-01T10:00:00Z"));

    @Test
    void transactionWithoutTriggeredRulesIsAllowed() {
        DecisionEvent decision = engine(silent("A"), silent("B")).decide(transaction);

        assertThat(decision.verdict()).isEqualTo(Verdict.ALLOW);
        assertThat(decision.riskScore()).isZero();
        assertThat(decision.triggeredRules()).isEmpty();
    }

    @Test
    void scoresOfTriggeredRulesAreSummedUpAndKeptForExplanation() {
        DecisionEvent decision = engine(firing("A", 30), silent("B"), firing("C", 60)).decide(transaction);

        assertThat(decision.verdict()).isEqualTo(Verdict.BLOCK);
        assertThat(decision.riskScore()).isEqualTo(90);
        assertThat(decision.triggeredRules()).containsExactly(
                new RuleHit("A", 30, "A fired"),
                new RuleHit("C", 60, "C fired"));
    }

    @Test
    void decisionCarriesEventTimeOfTransactionAndTimeOfDecision() {
        DecisionEvent decision = engine().decide(transaction);

        assertThat(decision.transactionId()).isEqualTo(transaction.transactionId());
        assertThat(decision.cardId()).isEqualTo(transaction.cardId());
        assertThat(decision.eventTime()).isEqualTo(transaction.timestamp());
        assertThat(decision.decidedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @CsvSource({"0, ALLOW", "39, ALLOW", "40, REVIEW", "79, REVIEW", "80, BLOCK", "250, BLOCK"})
    void thresholdsAreInclusive(int riskScore, Verdict expected) {
        assertThat(SCORING.verdictFor(riskScore)).isEqualTo(expected);
    }

    @Test
    void reviewThresholdAboveBlockThresholdIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ScoringProperties(90, 80));
    }

    private static DecisionEngine engine(FraudRule... rules) {
        return new DecisionEngine(List.of(rules), SCORING, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static FraudRule silent(String code) {
        return new StubRule(code, Optional.empty());
    }

    private static FraudRule firing(String code, int score) {
        return new StubRule(code, Optional.of(new RuleHit(code, score, code + " fired")));
    }

    private record StubRule(String code, Optional<RuleHit> result) implements FraudRule {

        @Override
        public Optional<RuleHit> evaluate(TransactionEvent transaction) {
            return result;
        }
    }
}
