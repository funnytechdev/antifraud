package com.antifraud.service.rules;

import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.TransactionEvent;
import com.antifraud.service.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.antifraud.service.TestTransactions.newCardId;
import static com.antifraud.service.TestTransactions.purchase;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs the rule against a real Redis: the logic lives in a Lua script. */
@IntegrationTest
class VelocityRuleIntegrationTest {

    private static final Instant START = Instant.parse("2026-03-01T10:00:00Z");

    @Autowired
    private VelocityRule rule;

    @Test
    void firesOnTheTenthTransactionWithinTwoMinutes() {
        List<TransactionEvent> purchases = purchases(newCardId(), 11, Duration.ofSeconds(10));

        List<Optional<RuleHit>> results = purchases.stream().map(rule::evaluate).toList();

        assertThat(results.subList(0, 9)).allSatisfy(result -> assertThat(result).isEmpty());
        assertThat(results.get(9)).hasValueSatisfying(hit -> {
            assertThat(hit.rule()).isEqualTo(VelocityRule.CODE);
            assertThat(hit.score()).isEqualTo(50);
            assertThat(hit.reason()).isEqualTo("10 transactions on the card within 120 seconds");
        });
        assertThat(results.get(10)).hasValueSatisfying(hit -> assertThat(hit.reason()).startsWith("11 transactions"));
    }

    @Test
    void staysSilentWhenTransactionsAreSpreadWiderThanTheWindow() {
        // Evaluated back to back, but by event time there is one purchase a minute.
        List<TransactionEvent> purchases = purchases(newCardId(), 30, Duration.ofMinutes(1));

        assertThat(purchases.stream().map(rule::evaluate)).allSatisfy(result -> assertThat(result).isEmpty());
    }

    @Test
    void forgetsTransactionsThatLeftTheWindow() {
        String cardId = newCardId();
        purchases(cardId, 9, Duration.ofSeconds(5)).forEach(rule::evaluate);

        Optional<RuleHit> afterPause = rule.evaluate(purchase(cardId, START.plus(Duration.ofMinutes(5))));

        assertThat(afterPause).isEmpty();
    }

    @Test
    void redeliveredTransactionIsNotCountedTwice() {
        String cardId = newCardId();
        List<TransactionEvent> purchases = purchases(cardId, 9, Duration.ofSeconds(5));

        purchases.forEach(rule::evaluate);
        List<Optional<RuleHit>> redelivered = purchases.stream().map(rule::evaluate).toList();
        Optional<RuleHit> tenth = rule.evaluate(purchase(cardId, START.plusSeconds(60)));

        assertThat(redelivered).allSatisfy(result -> assertThat(result).isEmpty());
        assertThat(tenth).hasValueSatisfying(hit -> assertThat(hit.reason()).startsWith("10 transactions"));
    }

    @Test
    void countsEveryCardSeparately() {
        purchases(newCardId(), 9, Duration.ofSeconds(5)).forEach(rule::evaluate);

        Optional<RuleHit> otherCard = rule.evaluate(purchase(newCardId(), START.plusSeconds(60)));

        assertThat(otherCard).isEmpty();
    }

    private static List<TransactionEvent> purchases(String cardId, int count, Duration interval) {
        return IntStream.range(0, count)
                .mapToObj(i -> purchase(cardId, START.plus(interval.multipliedBy(i))))
                .toList();
    }
}
