package com.antifraud.service.rules;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Wraps every Redis call made on the scoring path in a circuit breaker.
 *
 * <p>While Redis answers, the breaker is transparent. When calls start to fail or time out, it
 * opens after a few of them, and from then on calls fail at once instead of waiting for a timeout
 * each: a transaction is still decided within milliseconds, by the rules that do not need Redis.
 * After a pause the breaker lets a few calls through and closes again if they succeed.
 */
@Component
public class RedisGuard {

    public static final String CIRCUIT_BREAKER = "redis";

    private final CircuitBreaker breaker;

    public RedisGuard(CircuitBreakerRegistry registry) {
        this.breaker = registry.circuitBreaker(CIRCUIT_BREAKER);
    }

    /**
     * @throws StateUnavailableException if Redis failed or the breaker is open
     */
    public <T> T call(Supplier<T> redisCall) {
        try {
            return breaker.executeSupplier(redisCall);
        } catch (CallNotPermittedException e) {
            throw new StateUnavailableException("Redis circuit breaker is open", e);
        } catch (DataAccessException e) {
            throw new StateUnavailableException("Redis is unavailable: " + e.getMostSpecificCause().getMessage(), e);
        }
    }

    public CircuitBreaker.State state() {
        return breaker.getState();
    }
}
