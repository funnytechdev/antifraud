package com.antifraud.service.rules;

import com.antifraud.contracts.TransactionEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Fires when a card makes too many purchases within a short time, which is typical for card
 * testing.
 *
 * <p>The window slides over event time: it is counted back from the moment of the purchase, not
 * from the moment of processing, so the result does not depend on how far the consumer lags.
 */
@Component
public class VelocityRule implements FraudRule<VelocityRule.Parameters> {

    public static final String CODE = "VELOCITY";

    private static final RedisScript<Long> SCRIPT =
            RedisScript.of(new ClassPathResource("redis/velocity.lua"), Long.class);

    private final StringRedisTemplate redis;
    private final Duration stateTtl;

    public VelocityRule(StringRedisTemplate redis, StateProperties state) {
        this.redis = redis;
        this.stateTtl = state.velocityTtl();
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
        Duration window = Duration.ofSeconds(parameters.windowSeconds());
        Long count = redis.execute(SCRIPT,
                List.of(key(transaction.cardId())),
                String.valueOf(transaction.timestamp().toEpochMilli()),
                String.valueOf(window.toMillis()),
                transaction.transactionId().toString(),
                // The window can be made longer through the API than the configured time to live.
                String.valueOf(Math.max(stateTtl.toMillis(), window.toMillis())));

        if (count == null || count < parameters.threshold()) {
            return RuleResult.passed();
        }
        return RuleResult.fired("%d transactions on the card within %d seconds"
                .formatted(count, parameters.windowSeconds()));
    }

    /** The card id is wrapped in a hash tag so the key stays on one node in Redis Cluster. */
    private static String key(String cardId) {
        return "velocity:{" + cardId + "}";
    }

    /**
     * @param threshold     number of transactions within the window, the current one included, at
     *                      which the rule fires
     * @param windowSeconds length of the sliding window in seconds of event time
     */
    public record Parameters(int threshold, int windowSeconds) {

        public Parameters {
            if (threshold < 2) {
                throw new IllegalArgumentException("threshold must be at least 2");
            }
            if (windowSeconds < 1 || windowSeconds > 86_400) {
                throw new IllegalArgumentException("windowSeconds must be between 1 and 86400");
            }
        }
    }
}
