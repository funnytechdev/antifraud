package com.antifraud.service.ingest;

import com.antifraud.contracts.DecisionEvent;
import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.Verdict;
import com.antifraud.service.IntegrationTest;
import com.antifraud.service.decision.DecisionEngine;
import com.antifraud.service.rules.RedisGuard;
import com.antifraud.service.rules.RiskyCategoryAtNightRule;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.time.Instant;

import static com.antifraud.service.TestTransactions.newCardId;
import static com.antifraud.service.TestTransactions.purchase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Freezes the Redis container in the middle of the stream: its port stays open, but nothing
 * answers, which is the nastiest kind of outage. Transactions must keep getting decisions.
 */
@IntegrationTest
class RedisOutageIntegrationTest {

    /** 03:30 in Moscow. */
    private static final Instant NIGHT = Instant.parse("2026-03-01T00:30:00Z");

    @Autowired
    private TransactionProcessor processor;
    @Autowired
    private DecisionEngine engine;
    @Autowired
    private RedisGuard guard;
    @Autowired
    @Qualifier("redisContainer")
    private GenericContainer<?> redis;

    @Test
    void transactionsAreStillDecidedWhileRedisIsDownAndScoredFullyAfterwards() {
        freezeRedis();
        try {
            DecisionEvent large = processor.process(purchase(newCardId()).amount("6000").build());
            DecisionEvent small = processor.process(purchase(newCardId()).amount("1000").build());

            assertThat(large.verdict()).isEqualTo(Verdict.REVIEW);
            assertThat(degradation(large).reason()).startsWith("Not evaluated: AMOUNT_ANOMALY, BLACKLISTED_CARD, "
                    + "BLACKLISTED_MERCHANT, IMPOSSIBLE_TRAVEL, VELOCITY");
            assertThat(small.verdict()).isEqualTo(Verdict.ALLOW);
            assertThat(degradation(small).reason()).endsWith("fallback verdict for this amount: ALLOW");

            // Ten failed calls are enough to open the breaker; from then on there is no waiting.
            assertThat(guard.state()).isEqualTo(CircuitBreaker.State.OPEN);
            long started = System.nanoTime();
            DecisionEvent fast = engine.decide(purchase(newCardId()).amount("1000").build());
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(50));
            assertThat(degradation(fast).reason()).contains("circuit breaker is open");

            // A rule that needs no state keeps working.
            DecisionEvent gambling = processor.process(
                    purchase(newCardId()).mcc("7995").amount("35000").at(NIGHT).build());
            assertThat(gambling.triggeredRules()).extracting(RuleHit::rule)
                    .containsExactly(RiskyCategoryAtNightRule.CODE, DecisionEngine.DEGRADED);
            assertThat(gambling.verdict()).isEqualTo(Verdict.REVIEW);
        } finally {
            unfreezeRedis();
            // The breaker probes Redis again after a pause; other tests need it closed.
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).until(() ->
                    engine.decide(purchase(newCardId()).build()).triggeredRules().isEmpty()
                            && guard.state() == CircuitBreaker.State.CLOSED);
        }

        DecisionEvent recovered = processor.process(purchase(newCardId()).amount("6000").build());
        assertThat(recovered.verdict()).isEqualTo(Verdict.ALLOW);
        assertThat(recovered.triggeredRules()).isEmpty();
    }

    private static RuleHit degradation(DecisionEvent decision) {
        return decision.triggeredRules().stream()
                .filter(hit -> hit.rule().equals(DecisionEngine.DEGRADED))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The decision is not marked as degraded: " + decision));
    }

    private void freezeRedis() {
        DockerClientFactory.instance().client().pauseContainerCmd(redis.getContainerId()).exec();
    }

    private void unfreezeRedis() {
        DockerClientFactory.instance().client().unpauseContainerCmd(redis.getContainerId()).exec();
    }
}
