package com.antifraud.service.rules;

import com.antifraud.contracts.TransactionEvent;
import com.antifraud.service.TestTransactions.Place;
import com.antifraud.service.rules.RiskyCategoryAtNightRule.Parameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.Set;

import static com.antifraud.service.TestTransactions.MOSCOW;
import static com.antifraud.service.TestTransactions.newCardId;
import static com.antifraud.service.TestTransactions.purchase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RiskyCategoryAtNightRuleTest {

    private static final Parameters DEFAULTS = new Parameters(Set.of("4829", "5944", "6051", "7995"), 20_000, 0, 6);
    private static final Place VLADIVOSTOK = new Place("Vladivostok", 43.1155, 131.8855, "Asia/Vladivostok");
    /** 03:30 in Moscow (UTC+3). */
    private static final Instant MOSCOW_NIGHT = Instant.parse("2026-03-01T00:30:00Z");

    private final RiskyCategoryAtNightRule rule = new RiskyCategoryAtNightRule();

    @Test
    void largeGamblingPaymentAtNightFires() {
        RuleResult result = rule.evaluate(gambling("35000.00", MOSCOW, MOSCOW_NIGHT), DEFAULTS);

        assertThat(result).isEqualTo(RuleResult.fired(
                "Purchase of 35000.00 RUB at 03:30 local time (Europe/Moscow), gambling (MCC 7995)"));
    }

    @Test
    void nightIsTheLocalTimeOfTheMerchant() {
        // 20:00 in Moscow is 03:00 in Vladivostok.
        Instant moscowEvening = Instant.parse("2026-03-01T17:00:00Z");

        assertThat(rule.evaluate(gambling("35000", MOSCOW, moscowEvening), DEFAULTS)).isEqualTo(RuleResult.passed());
        assertThat(rule.evaluate(gambling("35000", VLADIVOSTOK, moscowEvening), DEFAULTS))
                .isInstanceOf(RuleResult.Fired.class);
    }

    @Test
    void eachConditionAloneIsNotEnough() {
        Instant moscowDay = Instant.parse("2026-03-01T11:00:00Z");

        assertThat(rule.evaluate(gambling("35000", MOSCOW, moscowDay), DEFAULTS)).isEqualTo(RuleResult.passed());
        assertThat(rule.evaluate(gambling("19999.99", MOSCOW, MOSCOW_NIGHT), DEFAULTS)).isEqualTo(RuleResult.passed());
        TransactionEvent groceries = purchase(newCardId()).mcc("5411").amount("35000").at(MOSCOW_NIGHT).build();
        assertThat(rule.evaluate(groceries, DEFAULTS)).isEqualTo(RuleResult.passed());
    }

    @ParameterizedTest
    @CsvSource({"0, 6, 0, true", "0, 6, 5, true", "0, 6, 6, false", "0, 6, 23, false",
            "23, 5, 23, true", "23, 5, 2, true", "23, 5, 5, false", "23, 5, 12, false"})
    void nightMayCrossMidnight(int start, int end, int hour, boolean night) {
        assertThat(new Parameters(Set.of("7995"), 0, start, end).isNight(hour)).isEqualTo(night);
    }

    @Test
    void unknownTimeZoneFallsBackToUtcInsteadOfFailing() {
        Place nowhere = new Place("Nowhere", 0, 0, "Mars/Olympus_Mons");

        RuleResult result = rule.evaluate(gambling("35000", nowhere, Instant.parse("2026-03-01T02:00:00Z")), DEFAULTS);

        assertThat(result).isInstanceOfSatisfying(RuleResult.Fired.class,
                fired -> assertThat(fired.reason()).contains("02:00 local time (Z)"));
    }

    @Test
    void invalidParametersAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Parameters(Set.of(), 0, 0, 6));
        assertThatIllegalArgumentException().isThrownBy(() -> new Parameters(Set.of("79"), 0, 0, 6));
        assertThatIllegalArgumentException().isThrownBy(() -> new Parameters(Set.of("7995"), 0, 3, 3));
        assertThatIllegalArgumentException().isThrownBy(() -> new Parameters(Set.of("7995"), 0, 0, 24));
    }

    private static TransactionEvent gambling(String amount, Place place, Instant timestamp) {
        return purchase(newCardId()).mcc("7995").amount(amount).in(place).at(timestamp).build();
    }
}
