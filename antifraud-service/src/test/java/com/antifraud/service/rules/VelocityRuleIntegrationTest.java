package com.antifraud.service.rules;

import com.antifraud.contracts.TransactionEvent;
import com.antifraud.service.IntegrationTest;
import com.antifraud.service.rules.VelocityRule.Parameters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static com.antifraud.service.TestTransactions.newCardId;
import static com.antifraud.service.TestTransactions.purchase;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs the rule against a real Redis: the logic lives in a Lua script. */
@IntegrationTest
class VelocityRuleIntegrationTest {

    private static final Instant START = Instant.parse("2026-03-01T10:00:00Z");
    private static final Parameters TEN_IN_TWO_MINUTES = new Parameters(10, 120);

    @Autowired
    private VelocityRule rule;

    @Test
    void firesOnTheTenthTransactionWithinTwoMinutes() {
        List<RuleResult> results = evaluate(purchases(newCardId(), 11, Duration.ofSeconds(10)));

        assertThat(results.subList(0, 9)).containsOnly(RuleResult.passed());
        assertThat(results.get(9)).isEqualTo(RuleResult.fired("10 transactions on the card within 120 seconds"));
        assertThat(results.get(10)).isEqualTo(RuleResult.fired("11 transactions on the card within 120 seconds"));
    }

    @Test
    void staysSilentWhenTransactionsAreSpreadWiderThanTheWindow() {
        // Evaluated back to back, but by event time there is one purchase a minute.
        List<RuleResult> results = evaluate(purchases(newCardId(), 30, Duration.ofMinutes(1)));

        assertThat(results).containsOnly(RuleResult.passed());
    }

    @Test
    void forgetsTransactionsThatLeftTheWindow() {
        String cardId = newCardId();
        evaluate(purchases(cardId, 9, Duration.ofSeconds(5)));

        RuleResult afterPause = rule.evaluate(purchase(cardId, START.plus(Duration.ofMinutes(5))), TEN_IN_TWO_MINUTES);

        assertThat(afterPause).isEqualTo(RuleResult.passed());
    }

    @Test
    void redeliveredTransactionIsNotCountedTwice() {
        String cardId = newCardId();
        List<TransactionEvent> purchases = purchases(cardId, 9, Duration.ofSeconds(5));

        evaluate(purchases);
        List<RuleResult> redelivered = evaluate(purchases);
        RuleResult tenth = rule.evaluate(purchase(cardId, START.plusSeconds(60)), TEN_IN_TWO_MINUTES);

        assertThat(redelivered).containsOnly(RuleResult.passed());
        assertThat(tenth).isEqualTo(RuleResult.fired("10 transactions on the card within 120 seconds"));
    }

    @Test
    void countsEveryCardSeparately() {
        evaluate(purchases(newCardId(), 9, Duration.ofSeconds(5)));

        RuleResult otherCard = rule.evaluate(purchase(newCardId(), START.plusSeconds(60)), TEN_IN_TWO_MINUTES);

        assertThat(otherCard).isEqualTo(RuleResult.passed());
    }

    @Test
    void usesTheParametersItIsGiven() {
        List<TransactionEvent> purchases = purchases(newCardId(), 3, Duration.ofSeconds(20));

        List<RuleResult> results = purchases.stream()
                .map(purchase -> rule.evaluate(purchase, new Parameters(3, 60)))
                .toList();

        assertThat(results).containsExactly(RuleResult.passed(), RuleResult.passed(),
                RuleResult.fired("3 transactions on the card within 60 seconds"));
    }

    private List<RuleResult> evaluate(List<TransactionEvent> purchases) {
        return purchases.stream().map(purchase -> rule.evaluate(purchase, TEN_IN_TWO_MINUTES)).toList();
    }

    private static List<TransactionEvent> purchases(String cardId, int count, Duration interval) {
        return IntStream.range(0, count)
                .mapToObj(i -> purchase(cardId, START.plus(interval.multipliedBy(i))))
                .toList();
    }
}
