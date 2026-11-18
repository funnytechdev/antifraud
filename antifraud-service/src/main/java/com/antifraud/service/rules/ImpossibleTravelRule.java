package com.antifraud.service.rules;

import com.antifraud.contracts.TransactionEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Fires when the card would have had to move faster than a plane to make two purchases in a row,
 * e.g. in Moscow and fifteen minutes later in Novosibirsk. Usually one of the two is made with a
 * cloned card.
 *
 * <p>The last location of every card is kept in Redis. Time between the purchases is event time,
 * so a lagging consumer does not make a trip look slower or faster than it was.
 */
@Component
public class ImpossibleTravelRule implements FraudRule<ImpossibleTravelRule.Parameters> {

    public static final String CODE = "IMPOSSIBLE_TRAVEL";

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT =
            RedisScript.of(new ClassPathResource("redis/last-location.lua"), List.class);

    private final StringRedisTemplate redis;
    private final Duration stateTtl;

    public ImpossibleTravelRule(StringRedisTemplate redis, StateProperties state) {
        this.redis = redis;
        this.stateTtl = state.locationTtl();
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
        @SuppressWarnings("unchecked")
        List<String> previous = redis.execute(SCRIPT,
                List.of(key(transaction.cardId())),
                transaction.transactionId().toString(),
                Double.toString(transaction.latitude()),
                Double.toString(transaction.longitude()),
                Long.toString(transaction.timestamp().toEpochMilli()),
                transaction.city(),
                Long.toString(stateTtl.toMillis()));
        if (previous == null || previous.isEmpty()) {
            return RuleResult.passed();
        }

        double distanceKm = Geo.distanceKm(Double.parseDouble(previous.get(0)), Double.parseDouble(previous.get(1)),
                transaction.latitude(), transaction.longitude());
        if (distanceKm < parameters.minDistanceKm()) {
            return RuleResult.passed();
        }
        Duration between = Duration.between(Instant.ofEpochMilli(Long.parseLong(previous.get(2))),
                transaction.timestamp()).abs();
        double hours = between.toMillis() / 3_600_000.0;
        double speedKmh = hours == 0 ? Double.POSITIVE_INFINITY : distanceKm / hours;
        if (speedKmh <= parameters.maxSpeedKmh()) {
            return RuleResult.passed();
        }
        return RuleResult.fired(String.format(Locale.ROOT,
                "%s and %s are %.0f km apart, purchases %d min apart: %s, faster than %d km/h",
                previous.get(3), transaction.city(), distanceKm, between.toMinutes(),
                Double.isInfinite(speedKmh) ? "at the same moment" : String.format(Locale.ROOT, "%.0f km/h", speedKmh),
                parameters.maxSpeedKmh()));
    }

    private static String key(String cardId) {
        return "location:{" + cardId + "}";
    }

    /**
     * @param maxSpeedKmh   highest believable speed between two purchases, km/h; a plane does about 800
     * @param minDistanceKm shorter distances are ignored: coordinates are approximate, and two
     *                      purchases at opposite ends of one city look like a fast trip
     */
    public record Parameters(int maxSpeedKmh, int minDistanceKm) {

        public Parameters {
            if (maxSpeedKmh < 1) {
                throw new IllegalArgumentException("maxSpeedKmh must be positive");
            }
            if (minDistanceKm < 0) {
                throw new IllegalArgumentException("minDistanceKm must not be negative");
            }
        }
    }
}
