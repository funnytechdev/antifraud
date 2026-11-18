package com.antifraud.service.decision;

import com.antifraud.contracts.DecisionEvent;
import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.TransactionEvent;
import com.antifraud.contracts.Verdict;
import com.antifraud.service.TestTransactions;
import com.antifraud.service.rules.FraudRule;
import com.antifraud.service.rules.NoParameters;
import com.antifraud.service.rules.RuleResult;
import com.antifraud.service.rules.StateUnavailableException;
import com.antifraud.service.settings.ActiveRule;
import com.antifraud.service.settings.InvalidSettingsException;
import com.antifraud.service.settings.ScoringSettings;
import com.antifraud.service.settings.SettingsSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.antifraud.service.TestTransactions.newCardId;
import static com.antifraud.service.TestTransactions.purchase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionEngineTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:05.123456789Z");
    private static final ScoringSettings SCORING = new ScoringSettings(40, 80, NOW);
    private static final DegradationProperties REVIEW_FROM_5000 =
            new DegradationProperties(Verdict.REVIEW, new BigDecimal("5000"));

    private final TransactionEvent transaction =
            TestTransactions.purchase("card-1", Instant.parse("2026-03-01T10:00:00.987654321Z"));

    @Test
    void transactionWithoutTriggeredRulesIsAllowed() {
        DecisionEvent decision = engine(rule(silent("A"), 30), rule(silent("B"), 60)).decide(transaction);

        assertThat(decision.verdict()).isEqualTo(Verdict.ALLOW);
        assertThat(decision.riskScore()).isZero();
        assertThat(decision.triggeredRules()).isEmpty();
    }

    @Test
    void scoresFromSettingsAreSummedUpAndKeptForExplanation() {
        DecisionEvent decision = engine(rule(firing("A"), 30), rule(silent("B"), 100), rule(firing("C"), 60))
                .decide(transaction);

        assertThat(decision.verdict()).isEqualTo(Verdict.BLOCK);
        assertThat(decision.riskScore()).isEqualTo(90);
        assertThat(decision.triggeredRules()).containsExactly(
                new RuleHit("A", 30, "A fired"),
                new RuleHit("C", 60, "C fired"));
    }

    @Test
    void disabledRuleIsNotEvaluated() {
        FraudRule<NoParameters> mustNotRun = new StubRule("A", () -> {
            throw new AssertionError("A disabled rule was evaluated");
        });
        ActiveRule disabled = new ActiveRule(mustNotRun, false, 100, new NoParameters(), NOW);

        DecisionEvent decision = engine(disabled, rule(firing("B"), 40)).decide(transaction);

        assertThat(decision.verdict()).isEqualTo(Verdict.REVIEW);
        assertThat(decision.triggeredRules()).extracting(RuleHit::rule).containsExactly("B");
    }

    @Test
    void newSettingsApplyToTheNextTransaction() {
        AtomicReference<SettingsSnapshot> settings =
                new AtomicReference<>(new SettingsSnapshot(SCORING, List.of(rule(firing("A"), 50))));
        DecisionEngine engine = new DecisionEngine(settings::get, REVIEW_FROM_5000, Clock.fixed(NOW, ZoneOffset.UTC));

        Verdict before = engine.decide(transaction).verdict();
        settings.set(new SettingsSnapshot(new ScoringSettings(60, 90, NOW), List.of(rule(firing("A"), 50))));
        Verdict after = engine.decide(transaction).verdict();

        assertThat(before).isEqualTo(Verdict.REVIEW);
        assertThat(after).isEqualTo(Verdict.ALLOW);
    }

    @Test
    void decisionCarriesEventTimeOfTransactionAndTimeOfDecisionInMicroseconds() {
        DecisionEvent decision = engine().decide(transaction);

        assertThat(decision.transactionId()).isEqualTo(transaction.transactionId());
        assertThat(decision.cardId()).isEqualTo(transaction.cardId());
        assertThat(decision.eventTime()).isEqualTo(Instant.parse("2026-03-01T10:00:00.987654Z"));
        assertThat(decision.decidedAt()).isEqualTo(Instant.parse("2026-03-01T10:00:05.123456Z"));
    }

    @ParameterizedTest
    @CsvSource({"0, ALLOW", "39, ALLOW", "40, REVIEW", "79, REVIEW", "80, BLOCK", "250, BLOCK"})
    void thresholdsAreInclusive(int riskScore, Verdict expected) {
        assertThat(SCORING.verdictFor(riskScore)).isEqualTo(expected);
    }

    @Test
    void reviewThresholdAboveBlockThresholdIsRejected() {
        assertThatThrownBy(() -> new ScoringSettings(90, 80, NOW)).isInstanceOf(InvalidSettingsException.class);
    }

    @Test
    void largePurchaseGetsTheFallbackVerdictWhenRulesAreUnavailable() {
        DecisionEvent decision = engine(rule(unavailable("A"), 50), rule(unavailable("B"), 100), rule(silent("C"), 30))
                .decide(purchase(newCardId()).amount("5000").build());

        assertThat(decision.verdict()).isEqualTo(Verdict.REVIEW);
        assertThat(decision.riskScore()).isZero();
        assertThat(decision.triggeredRules()).containsExactly(new RuleHit(DecisionEngine.DEGRADED, 0,
                "Not evaluated: A, B (Redis is unavailable: connection refused); fallback verdict for this amount: REVIEW"));
    }

    @Test
    void smallPurchaseIsDecidedByTheRemainingRules() {
        DecisionEvent decision = engine(rule(unavailable("A"), 50), rule(silent("C"), 30))
                .decide(purchase(newCardId()).amount("4999.99").build());

        assertThat(decision.verdict()).isEqualTo(Verdict.ALLOW);
        assertThat(decision.triggeredRules()).extracting(RuleHit::rule).containsExactly(DecisionEngine.DEGRADED);
    }

    @Test
    void remainingRulesCanMakeTheVerdictStricterThanTheFallback() {
        DecisionEvent decision = engine(rule(unavailable("A"), 50), rule(firing("C"), 90))
                .decide(purchase(newCardId()).amount("100").build());

        assertThat(decision.verdict()).isEqualTo(Verdict.BLOCK);
        assertThat(decision.riskScore()).isEqualTo(90);
        assertThat(decision.triggeredRules()).extracting(RuleHit::rule).containsExactly("C", DecisionEngine.DEGRADED);
    }

    @ParameterizedTest
    @CsvSource({"ALLOW, ALLOW", "REVIEW, REVIEW", "BLOCK, BLOCK"})
    void fallbackVerdictIsConfigurable(Verdict fallback, Verdict expected) {
        DegradationProperties degradation = new DegradationProperties(fallback, BigDecimal.ZERO);

        DecisionEvent decision = engine(degradation, rule(unavailable("A"), 50)).decide(transaction);

        assertThat(decision.verdict()).isEqualTo(expected);
    }

    private static DecisionEngine engine(ActiveRule... rules) {
        return engine(REVIEW_FROM_5000, rules);
    }

    private static DecisionEngine engine(DegradationProperties degradation, ActiveRule... rules) {
        SettingsSnapshot snapshot = new SettingsSnapshot(SCORING, List.of(rules));
        return new DecisionEngine(() -> snapshot, degradation, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ActiveRule rule(FraudRule<NoParameters> rule, int score) {
        return new ActiveRule(rule, true, score, new NoParameters(), NOW);
    }

    private static FraudRule<NoParameters> silent(String code) {
        return new StubRule(code, RuleResult::passed);
    }

    private static FraudRule<NoParameters> firing(String code) {
        return new StubRule(code, () -> RuleResult.fired(code + " fired"));
    }

    private static FraudRule<NoParameters> unavailable(String code) {
        return new StubRule(code, () -> {
            throw new StateUnavailableException("Redis is unavailable: connection refused", null);
        });
    }

    private record StubRule(String code, Supplier<RuleResult> result) implements FraudRule<NoParameters> {

        @Override
        public Class<NoParameters> parametersType() {
            return NoParameters.class;
        }

        @Override
        public RuleResult evaluate(TransactionEvent transaction, NoParameters parameters) {
            return result.get();
        }
    }
}
