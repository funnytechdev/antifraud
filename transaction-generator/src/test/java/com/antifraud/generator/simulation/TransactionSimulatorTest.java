package com.antifraud.generator.simulation;

import com.antifraud.contracts.TransactionEvent;
import com.antifraud.generator.world.Card;
import com.antifraud.generator.world.Merchant;
import com.antifraud.generator.world.World;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionSimulatorTest {

    private static final Instant START = Instant.parse("2026-01-15T12:00:00Z");

    private final World world = World.generate(2_000, 100, 20, 5, new Random(1));
    private final Set<String> compromisedCards = ids(world.compromisedCards(), Card::id);
    private final Set<String> fraudMerchants = ids(world.fraudMerchants(), Merchant::id);

    @Test
    void honestTrafficIsUnlabelledAndAvoidsCompromisedCardsAndFraudMerchants() {
        TransactionSimulator simulator = new TransactionSimulator(world, new Random(2), 0.0);

        List<LabeledTransaction> batch = simulator.next(START, 1_000);

        assertThat(batch).hasSize(1_000).allSatisfy(transaction -> {
            TransactionEvent event = transaction.event();
            assertThat(transaction.isFraud()).isFalse();
            assertThat(event.timestamp()).isEqualTo(START);
            assertThat(event.amount()).isGreaterThanOrEqualTo(BigDecimal.ONE);
            assertThat(event.amount().scale()).isEqualTo(2);
            assertThat(compromisedCards).doesNotContain(event.cardId());
            assertThat(fraudMerchants).doesNotContain(event.merchantId());
        });
    }

    @Test
    void fraudScenariosUseTheMatchingCardsAndMerchants() {
        List<LabeledTransaction> fraud = runFraudOnly(300);

        assertThat(fraud).allSatisfy(transaction -> assertThat(transaction.isFraud()).isTrue());
        assertThat(only(fraud, FraudScenario.COMPROMISED_CARD)).isNotEmpty()
                .allSatisfy(event -> assertThat(compromisedCards).contains(event.cardId()));
        assertThat(only(fraud, FraudScenario.FRAUD_MERCHANT)).isNotEmpty()
                .allSatisfy(event -> assertThat(fraudMerchants).contains(event.merchantId()));
    }

    @Test
    void velocityBurstPutsAtLeastTwelvePurchasesOfOneCardWithinTwoMinutes() {
        Map<String, List<TransactionEvent>> burstsByCard = only(runFraudOnly(300), FraudScenario.VELOCITY_BURST)
                .stream()
                .collect(Collectors.groupingBy(TransactionEvent::cardId));

        assertThat(burstsByCard).isNotEmpty();
        assertThat(burstsByCard.values()).allSatisfy(purchases -> {
            List<Instant> times = purchases.stream().map(TransactionEvent::timestamp).sorted().toList();
            assertThat(purchases).hasSizeGreaterThanOrEqualTo(12);
            assertThat(Duration.between(times.getFirst(), times.getLast())).isLessThan(Duration.ofMinutes(2));
        });
    }

    @Test
    void plannedPurchasesAreReleasedOnlyWhenTheyAreDue() {
        TransactionSimulator simulator = new TransactionSimulator(world, new Random(3), 1.0);

        List<LabeledTransaction> immediate = simulator.next(START, 50);
        List<LabeledTransaction> afterOneSecond = simulator.next(START.plusSeconds(1), 0);
        List<LabeledTransaction> afterThreeMinutes = simulator.next(START.plusSeconds(180), 0);

        assertThat(immediate).hasSize(50)
                .allSatisfy(transaction -> assertThat(transaction.event().timestamp()).isEqualTo(START));
        // The next step of a burst comes no sooner than two seconds after the previous one.
        assertThat(afterOneSecond).isEmpty();
        assertThat(afterThreeMinutes).isNotEmpty()
                .allSatisfy(transaction -> assertThat(transaction.scenario()).isEqualTo(FraudScenario.VELOCITY_BURST));
        assertThat(simulator.next(START.plusSeconds(600), 0)).isEmpty();
    }

    /** Starts {@code scenarios} fraud scenarios at once and plays them to the end. */
    private List<LabeledTransaction> runFraudOnly(int scenarios) {
        TransactionSimulator simulator = new TransactionSimulator(world, new Random(3), 1.0);
        List<LabeledTransaction> all = new ArrayList<>(simulator.next(START, scenarios));
        all.addAll(simulator.next(START.plusSeconds(180), 0));
        return all;
    }

    private static List<TransactionEvent> only(List<LabeledTransaction> transactions, FraudScenario scenario) {
        return transactions.stream()
                .filter(transaction -> transaction.scenario() == scenario)
                .map(LabeledTransaction::event)
                .toList();
    }

    private static <T> Set<String> ids(List<T> items, java.util.function.Function<T, String> id) {
        return items.stream().map(id).collect(Collectors.toSet());
    }
}
