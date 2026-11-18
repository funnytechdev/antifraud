package com.antifraud.service.rules;

import com.antifraud.contracts.TransactionEvent;
import com.antifraud.service.IntegrationTest;
import com.antifraud.service.TestTransactions.Place;
import com.antifraud.service.rules.ImpossibleTravelRule.Parameters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;

import static com.antifraud.service.TestTransactions.MOSCOW;
import static com.antifraud.service.TestTransactions.NOVOSIBIRSK;
import static com.antifraud.service.TestTransactions.PODOLSK;
import static com.antifraud.service.TestTransactions.SAINT_PETERSBURG;
import static com.antifraud.service.TestTransactions.newCardId;
import static com.antifraud.service.TestTransactions.purchase;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ImpossibleTravelRuleIntegrationTest {

    private static final Instant START = Instant.parse("2026-03-01T10:00:00Z");
    private static final Parameters PLANE_SPEED = new Parameters(800, 300);

    @Autowired
    private ImpossibleTravelRule rule;

    @Test
    void firstPurchaseOfTheCardHasNothingToCompareWith() {
        assertThat(evaluate(newCardId(), NOVOSIBIRSK, START)).isEqualTo(RuleResult.passed());
    }

    @Test
    void firesWhenTheCardIsInNovosibirskFifteenMinutesAfterMoscow() {
        String cardId = newCardId();
        evaluate(cardId, MOSCOW, START);

        RuleResult result = evaluate(cardId, NOVOSIBIRSK, START.plus(Duration.ofMinutes(15)));

        assertThat(result).isInstanceOfSatisfying(RuleResult.Fired.class, fired -> assertThat(fired.reason())
                .startsWith("Moscow and Novosibirsk are 28")
                .contains("15 min apart")
                .endsWith("faster than 800 km/h"));
    }

    @Test
    void flightTimeIsBelievable() {
        String cardId = newCardId();
        evaluate(cardId, MOSCOW, START);

        // About 2 800 km in 5 hours: 560 km/h.
        assertThat(evaluate(cardId, NOVOSIBIRSK, START.plus(Duration.ofHours(5)))).isEqualTo(RuleResult.passed());
    }

    @Test
    void shortDistancesAreIgnoredWhateverTheSpeed() {
        String cardId = newCardId();
        evaluate(cardId, MOSCOW, START);

        assertThat(evaluate(cardId, PODOLSK, START.plusSeconds(60))).isEqualTo(RuleResult.passed());
    }

    @Test
    void purchasesAtTheSameMomentInDifferentCitiesFire() {
        String cardId = newCardId();
        evaluate(cardId, MOSCOW, START);

        assertThat(evaluate(cardId, SAINT_PETERSBURG, START)).isInstanceOfSatisfying(RuleResult.Fired.class,
                fired -> assertThat(fired.reason()).contains("at the same moment"));
    }

    @Test
    void comparesWithTheLatestPurchase() {
        String cardId = newCardId();
        evaluate(cardId, MOSCOW, START);
        evaluate(cardId, NOVOSIBIRSK, START.plus(Duration.ofHours(6)));

        // Novosibirsk again ten minutes later is fine, although Moscow was only six hours ago.
        assertThat(evaluate(cardId, NOVOSIBIRSK, START.plus(Duration.ofHours(6)).plus(Duration.ofMinutes(10))))
                .isEqualTo(RuleResult.passed());
    }

    @Test
    void redeliveredTransactionGetsTheSameResultAsTheFirstTime() {
        String cardId = newCardId();
        evaluate(cardId, MOSCOW, START);
        TransactionEvent jump = purchase(cardId).in(NOVOSIBIRSK).at(START.plus(Duration.ofMinutes(15))).build();

        RuleResult first = rule.evaluate(jump, PLANE_SPEED);
        RuleResult again = rule.evaluate(jump, PLANE_SPEED);

        assertThat(first).isInstanceOf(RuleResult.Fired.class);
        assertThat(again).isEqualTo(first);
    }

    @Test
    void lateEventDoesNotMoveTheCardBackInTime() {
        String cardId = newCardId();
        evaluate(cardId, NOVOSIBIRSK, START.plus(Duration.ofHours(1)));

        evaluate(cardId, NOVOSIBIRSK, START);

        // The card is still known to be in Novosibirsk at 11:00, not at 10:00.
        assertThat(evaluate(cardId, MOSCOW, START.plus(Duration.ofMinutes(90)))).isInstanceOf(RuleResult.Fired.class);
    }

    private RuleResult evaluate(String cardId, Place place, Instant timestamp) {
        return rule.evaluate(purchase(cardId).in(place).at(timestamp).build(), PLANE_SPEED);
    }
}
