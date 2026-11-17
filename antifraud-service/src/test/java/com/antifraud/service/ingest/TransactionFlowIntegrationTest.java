package com.antifraud.service.ingest;

import com.antifraud.contracts.DecisionEvent;
import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.TransactionEvent;
import com.antifraud.contracts.Verdict;
import com.antifraud.service.IntegrationTest;
import com.antifraud.service.blacklist.BlacklistService;
import com.antifraud.service.blacklist.BlacklistType;
import com.antifraud.service.decision.DecisionRepository;
import com.antifraud.service.decision.StoredDecision;
import com.antifraud.service.rules.BlacklistedCardRule;
import com.antifraud.service.rules.BlacklistedMerchantRule;
import com.antifraud.service.rules.VelocityRule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.antifraud.service.TestTransactions.newCardId;
import static com.antifraud.service.TestTransactions.newMerchantId;
import static com.antifraud.service.TestTransactions.purchase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Sends transactions to Kafka the way the generator does and checks what the service decides. */
@IntegrationTest
class TransactionFlowIntegrationTest {

    private static final Instant START = Instant.parse("2026-03-01T10:00:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, Object> kafka;
    @Autowired
    private KafkaContainer kafkaContainer;
    @Autowired
    private TopicsProperties topics;
    @Autowired
    private DecisionRepository decisions;
    @Autowired
    private BlacklistService blacklist;
    @Autowired
    private JsonMapper json;

    @Test
    void honestTransactionIsAllowedAndStoredWithItsDetails() {
        TransactionEvent transaction = purchase(newCardId(), START);

        send(transaction);

        StoredDecision decision = awaitDecision(transaction);
        assertThat(decision.verdict()).isEqualTo(Verdict.ALLOW);
        assertThat(decision.riskScore()).isZero();
        assertThat(decision.triggeredRules()).isEmpty();
        assertThat(decision.cardId()).isEqualTo(transaction.cardId());
        assertThat(decision.merchantId()).isEqualTo(transaction.merchantId());
        assertThat(decision.amount()).isEqualByComparingTo(transaction.amount());
        assertThat(decision.eventTime()).isEqualTo(START);
    }

    @Test
    void purchaseWithBlacklistedCardIsBlocked() {
        String cardId = newCardId();
        blacklist.add(BlacklistType.CARD, List.of(cardId));
        TransactionEvent transaction = purchase(cardId, START);

        send(transaction);

        StoredDecision decision = awaitDecision(transaction);
        assertThat(decision.verdict()).isEqualTo(Verdict.BLOCK);
        assertThat(decision.riskScore()).isEqualTo(100);
        assertThat(decision.triggeredRules()).extracting(RuleHit::rule).containsExactly(BlacklistedCardRule.CODE);
    }

    @Test
    void purchaseAtBlacklistedMerchantGoesToReview() {
        String merchantId = newMerchantId();
        blacklist.add(BlacklistType.MERCHANT, List.of(merchantId));
        TransactionEvent transaction = purchase(newCardId(), merchantId, START);

        send(transaction);

        StoredDecision decision = awaitDecision(transaction);
        assertThat(decision.verdict()).isEqualTo(Verdict.REVIEW);
        assertThat(decision.riskScore()).isEqualTo(60);
        assertThat(decision.triggeredRules()).singleElement().satisfies(hit -> {
            assertThat(hit.rule()).isEqualTo(BlacklistedMerchantRule.CODE);
            assertThat(hit.reason()).contains(merchantId);
        });
    }

    @Test
    void scoresOfAllTriggeredRulesAddUp() {
        String cardId = newCardId();
        String merchantId = newMerchantId();
        blacklist.add(BlacklistType.CARD, List.of(cardId));
        blacklist.add(BlacklistType.MERCHANT, List.of(merchantId));
        TransactionEvent transaction = purchase(cardId, merchantId, START);

        send(transaction);

        StoredDecision decision = awaitDecision(transaction);
        assertThat(decision.verdict()).isEqualTo(Verdict.BLOCK);
        assertThat(decision.riskScore()).isEqualTo(160);
        assertThat(decision.triggeredRules()).extracting(RuleHit::rule)
                .containsExactlyInAnyOrder(BlacklistedCardRule.CODE, BlacklistedMerchantRule.CODE);
    }

    @Test
    void tenthPurchaseOfCardWithinTwoMinutesGoesToReview() {
        String cardId = newCardId();
        List<TransactionEvent> purchases = IntStream.range(0, 10)
                .mapToObj(i -> purchase(cardId, START.plusSeconds(i * 10L)))
                .toList();

        purchases.forEach(this::send);

        List<StoredDecision> results = purchases.stream().map(this::awaitDecision).toList();
        assertThat(results.subList(0, 9)).allSatisfy(decision ->
                assertThat(decision.verdict()).isEqualTo(Verdict.ALLOW));
        StoredDecision tenth = results.getLast();
        assertThat(tenth.verdict()).isEqualTo(Verdict.REVIEW);
        assertThat(tenth.riskScore()).isEqualTo(50);
        assertThat(tenth.triggeredRules()).singleElement().satisfies(hit -> {
            assertThat(hit.rule()).isEqualTo(VelocityRule.CODE);
            assertThat(hit.reason()).contains("10 transactions");
        });
    }

    @Test
    void velocityWindowIsMeasuredInEventTimeNotInProcessingTime() {
        String cardId = newCardId();
        // The purchases reach the service within milliseconds, but they were made a minute apart.
        List<TransactionEvent> purchases = IntStream.range(0, 12)
                .mapToObj(i -> purchase(cardId, START.plus(Duration.ofMinutes(i))))
                .toList();

        purchases.forEach(this::send);

        assertThat(purchases.stream().map(this::awaitDecision)).allSatisfy(decision -> {
            assertThat(decision.verdict()).isEqualTo(Verdict.ALLOW);
            assertThat(decision.triggeredRules()).isEmpty();
        });
    }

    @Test
    void decisionIsPublishedToKafkaWithCardIdAsKey() {
        String cardId = newCardId();
        blacklist.add(BlacklistType.CARD, List.of(cardId));
        TransactionEvent transaction = purchase(cardId, START);

        send(transaction);

        ConsumerRecord<String, String> record = awaitDecisionRecord(cardId);
        DecisionEvent decision = json.readValue(record.value(), DecisionEvent.class);
        assertThat(decision.transactionId()).isEqualTo(transaction.transactionId());
        assertThat(decision.verdict()).isEqualTo(Verdict.BLOCK);
        assertThat(decision.riskScore()).isEqualTo(100);
        assertThat(decision.triggeredRules()).extracting(RuleHit::rule).containsExactly(BlacklistedCardRule.CODE);
        assertThat(decision.eventTime()).isEqualTo(START);
    }

    private void send(TransactionEvent transaction) {
        kafka.send(topics.transactions(), transaction.cardId(), transaction);
    }

    private StoredDecision awaitDecision(TransactionEvent transaction) {
        return await().atMost(TIMEOUT)
                .until(() -> decisions.findById(transaction.transactionId()), Optional::isPresent)
                .orElseThrow();
    }

    private ConsumerRecord<String, String> awaitDecisionRecord(String cardId) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topics.decisions()));
            Instant deadline = Instant.now().plus(TIMEOUT);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (cardId.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("No decision for card %s in topic %s".formatted(cardId, topics.decisions()));
    }
}
