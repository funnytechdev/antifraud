package com.antifraud.service.rules;

import com.antifraud.contracts.TransactionEvent;
import com.antifraud.service.IntegrationTest;
import com.antifraud.service.rules.AmountAnomalyRule.Parameters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Locale;

import static com.antifraud.service.TestTransactions.newCardId;
import static com.antifraud.service.TestTransactions.newCustomerId;
import static com.antifraud.service.TestTransactions.purchase;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class AmountAnomalyRuleIntegrationTest {

    private static final Parameters DEFAULTS = new Parameters(3.0, 10, 10_000);
    /** Twenty purchases of a customer who usually spends about 2 000 RUB. */
    private static final String[] HISTORY = {
            "1800", "2100", "1950", "2400", "1600", "2050", "2300", "1700", "2200", "1900",
            "2500", "1850", "2150", "1750", "2000", "2250", "1650", "2350", "1950", "2050"};

    @Autowired
    private AmountAnomalyRule rule;

    @Test
    void firesWhenTheCustomerPaysManyTimesMoreThanUsual() {
        String customer = newCustomerId();
        assertThat(feed(customer, HISTORY)).containsOnly(RuleResult.passed());

        RuleResult result = evaluate(customer, "150000.00");

        assertThat(result).isEqualTo(RuleResult.fired(expectedReason("150000.00", HISTORY)));
    }

    @Test
    void largerButOrdinaryPurchaseDoesNotFire() {
        String customer = newCustomerId();
        feed(customer, HISTORY);

        // Twice as much as usual, and above the minimal amount once the threshold is lowered.
        assertThat(rule.evaluate(purchaseOf(customer, "4500"), new Parameters(3.0, 10, 0)))
                .isEqualTo(RuleResult.passed());
    }

    @Test
    void newCustomerHasNoUsualAmountYet() {
        String customer = newCustomerId();
        feed(customer, Arrays.copyOf(HISTORY, 9));

        assertThat(evaluate(customer, "150000.00")).isEqualTo(RuleResult.passed());
    }

    @Test
    void smallAmountsNeverFire() {
        String customer = newCustomerId();
        feed(customer, "100", "120", "90", "110", "100", "95", "105", "115", "85", "100");

        // Eighty times the usual amount, but still below 10 000 RUB.
        assertThat(evaluate(customer, "8000")).isEqualTo(RuleResult.passed());
    }

    @Test
    void customerWhoAlwaysPaysTheSameIsNotFlaggedForASmallChange() {
        String customer = newCustomerId();
        feed(customer, "2000", "2000", "2000", "2000", "2000", "2000", "2000", "2000", "2000", "2000");

        assertThat(rule.evaluate(purchaseOf(customer, "2600"), new Parameters(3.0, 10, 0)))
                .isEqualTo(RuleResult.passed());
    }

    @Test
    void redeliveredTransactionIsAnsweredTheSameAndCountedOnce() {
        String customer = newCustomerId();
        feed(customer, HISTORY);
        TransactionEvent anomaly = purchaseOf(customer, "150000.00");

        RuleResult first = rule.evaluate(anomaly, DEFAULTS);
        RuleResult again = rule.evaluate(anomaly, DEFAULTS);
        RuleResult next = evaluate(customer, "150000.00");

        assertThat(again).isEqualTo(first);
        // 21 purchases before the next one: the redelivered transaction was counted once.
        assertThat(next).isInstanceOfSatisfying(RuleResult.Fired.class,
                fired -> assertThat(fired.reason()).endsWith("over the last 21"));
    }

    @Test
    void statisticsBelongToTheCustomerNotToTheCard() {
        String customer = newCustomerId();
        feed(customer, HISTORY);

        // A second card of the same customer inherits the habits.
        RuleResult result = rule.evaluate(purchase(newCardId()).customer(customer).amount("150000.00").build(), DEFAULTS);

        assertThat(result).isInstanceOf(RuleResult.Fired.class);
    }

    private RuleResult[] feed(String customer, String... amounts) {
        return Arrays.stream(amounts).map(amount -> evaluate(customer, amount)).toArray(RuleResult[]::new);
    }

    private RuleResult evaluate(String customer, String amount) {
        return rule.evaluate(purchaseOf(customer, amount), DEFAULTS);
    }

    private static TransactionEvent purchaseOf(String customer, String amount) {
        return purchase(newCardId()).customer(customer).amount(amount).build();
    }

    /** The same statistics computed the textbook way, in two passes, with the same floor of the deviation. */
    private static String expectedReason(String amount, String[] history) {
        double[] logs = Arrays.stream(history).mapToDouble(value -> Math.log(Double.parseDouble(value))).toArray();
        double mean = Arrays.stream(logs).average().orElseThrow();
        double variance = Arrays.stream(logs).map(x -> (x - mean) * (x - mean)).sum() / (logs.length - 1);
        double stdDev = Math.max(Math.sqrt(variance), AmountAnomalyRule.MIN_STD_DEV);
        double zScore = (Math.log(Double.parseDouble(amount)) - mean) / stdDev;
        return String.format(Locale.ROOT, "Amount %s RUB is %.1f standard deviations above the customer's usual "
                + "purchases: typically %.0f RUB over the last %d", amount, zScore, Math.exp(mean), logs.length);
    }
}
