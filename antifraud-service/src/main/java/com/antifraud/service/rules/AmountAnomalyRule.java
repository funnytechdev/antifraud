package com.antifraud.service.rules;

import com.antifraud.contracts.TransactionEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Fires when a customer pays far more than usual: typically 2 000 RUB, and suddenly 150 000 RUB.
 *
 * <p>The mean and the variance of the customer's purchases are kept in Redis and updated with every
 * transaction. They are computed over the logarithm of the amount: purchase amounts are roughly
 * log-normal, so "how many times more than usual" is a better measure than "how many roubles more".
 * On the plain scale a single trip to a jewelry store would stretch the variance so much that nothing
 * would ever look anomalous again.
 *
 * <p>Every transaction updates the statistics, the anomalous one included: the rule cannot tell a
 * stolen card from a new expensive habit.
 */
@Component
public class AmountAnomalyRule implements FraudRule<AmountAnomalyRule.Parameters> {

    public static final String CODE = "AMOUNT_ANOMALY";

    /**
     * Lower bound of the standard deviation, on the log scale. A customer who always pays the same
     * would otherwise have a deviation close to zero and trip the rule on any change at all. 0.5 on
     * the log scale is a factor of about 1.65: with the default threshold of three deviations a purchase
     * has to be at least 4.5 times the usual one to fire.
     */
    static final double MIN_STD_DEV = 0.5;

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT =
            RedisScript.of(new ClassPathResource("redis/amount-stats.lua"), List.class);

    private final StringRedisTemplate redis;
    private final RedisGuard guard;
    private final Duration stateTtl;

    public AmountAnomalyRule(StringRedisTemplate redis, RedisGuard guard, StateProperties state) {
        this.redis = redis;
        this.guard = guard;
        this.stateTtl = state.amountStatsTtl();
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public RuleResult evaluate(TransactionEvent transaction, Parameters parameters) {
        double logAmount = Math.log(transaction.amount().doubleValue());
        @SuppressWarnings("unchecked")
        List<String> before = guard.call(() -> redis.execute(SCRIPT,
                List.of(key(transaction.customerId())),
                transaction.transactionId().toString(),
                Double.toString(logAmount),
                Long.toString(stateTtl.toMillis())));
        if (before == null || before.size() < 3) {
            return RuleResult.passed();
        }

        long count = Long.parseLong(before.get(0));
        if (count < parameters.minHistory()
                || transaction.amount().compareTo(BigDecimal.valueOf(parameters.minAmount())) < 0) {
            return RuleResult.passed();
        }
        double mean = Double.parseDouble(before.get(1));
        double variance = Double.parseDouble(before.get(2)) / (count - 1);
        double stdDev = Math.max(Math.sqrt(variance), MIN_STD_DEV);
        double zScore = (logAmount - mean) / stdDev;
        if (zScore < parameters.zScoreThreshold()) {
            return RuleResult.passed();
        }
        return RuleResult.fired(String.format(Locale.ROOT, "Amount %.2f %s is %.1f standard deviations above "
                        + "the customer's usual purchases: typically %.0f %s over the last %d",
                transaction.amount(), transaction.currency(), zScore, Math.exp(mean),
                transaction.currency(), count));
    }

    private static String key(String customerId) {
        return "amount:{" + customerId + "}";
    }

    /**
     * @param zScoreThreshold how many standard deviations above the customer's mean, on the log
     *                        scale, make an amount anomalous
     * @param minHistory      number of earlier purchases needed before the customer's habits are
     *                        trusted; a new customer has no "usual" yet
     * @param minAmount       smaller amounts never fire, however unusual: they are not worth a
     *                        customer's annoyance
     */
    public record Parameters(double zScoreThreshold, int minHistory, long minAmount) {

        public Parameters {
            if (zScoreThreshold <= 0) {
                throw new IllegalArgumentException("zScoreThreshold must be positive");
            }
            if (minHistory < 2) {
                throw new IllegalArgumentException("minHistory must be at least 2");
            }
            if (minAmount < 0) {
                throw new IllegalArgumentException("minAmount must not be negative");
            }
        }
    }
}
