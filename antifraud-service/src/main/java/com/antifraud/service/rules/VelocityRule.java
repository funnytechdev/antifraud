package com.antifraud.service.rules;

import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.TransactionEvent;
import com.antifraud.service.rules.RuleProperties.Velocity;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Fires when a card makes too many purchases within a short time, which is typical for card
 * testing.
 *
 * <p>The window slides over event time: it is counted back from the moment of the purchase, not
 * from the moment of processing, so the result does not depend on how far the consumer lags.
 */
@Component
public class VelocityRule implements FraudRule {

    public static final String CODE = "VELOCITY";

    private static final RedisScript<Long> SCRIPT =
            RedisScript.of(new ClassPathResource("redis/velocity.lua"), Long.class);

    private final StringRedisTemplate redis;
    private final Velocity settings;

    public VelocityRule(StringRedisTemplate redis, RuleProperties properties) {
        this.redis = redis;
        this.settings = properties.velocity();
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RuleHit> evaluate(TransactionEvent transaction) {
        Long count = redis.execute(SCRIPT,
                List.of(key(transaction.cardId())),
                String.valueOf(transaction.timestamp().toEpochMilli()),
                String.valueOf(settings.window().toMillis()),
                transaction.transactionId().toString(),
                String.valueOf(settings.stateTtl().toMillis()));

        if (count == null || count < settings.threshold()) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit(CODE, settings.score(),
                "%d transactions on the card within %d seconds".formatted(count, settings.window().toSeconds())));
    }

    /** The card id is wrapped in a hash tag so the key stays on one node in Redis Cluster. */
    private static String key(String cardId) {
        return "velocity:{" + cardId + "}";
    }
}
