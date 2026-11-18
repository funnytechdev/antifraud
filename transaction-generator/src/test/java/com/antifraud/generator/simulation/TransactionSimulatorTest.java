package com.antifraud.generator.simulation;

import com.antifraud.contracts.TransactionEvent;
import com.antifraud.generator.world.Card;
import com.antifraud.generator.world.City;
import com.antifraud.generator.world.Merchant;
import com.antifraud.generator.world.MerchantCategory;
import com.antifraud.generator.world.World;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionSimulatorTest {

    /** 15:00 in Moscow, 22:00 in Vladivostok: night nowhere. */
    private static final Instant DAY = Instant.parse("2026-01-15T12:00:00Z");
    /** Midnight in Moscow, 4 a.m. in Novosibirsk. */
    private static final Instant NIGHT = Instant.parse("2026-01-15T21:00:00Z");

    private final World world = World.generate(2_000, 300, 20, 5, new Random(1));
    private final Set<String> compromisedCards = ids(world.compromisedCards(), Card::id);
    private final Set<String> fraudMerchants = ids(world.fraudMerchants(), Merchant::id);
    private final Map<String, Card> cards = world.activeCards().stream()
            .collect(Collectors.toMap(Card::id, Function.identity()));
    private final Map<String, City> cities = City.ALL.stream()
            .collect(Collectors.toMap(City::name, Function.identity()));

    @Test
    void honestTrafficIsUnlabelledAndAvoidsCompromisedCardsAndFraudMerchants() {
        TransactionSimulator simulator = new TransactionSimulator(world, new Random(2), 0.0);

        List<LabeledTransaction> batch = simulator.next(DAY, 1_000);

        assertThat(batch).hasSize(1_000).allSatisfy(transaction -> {
            TransactionEvent event = transaction.event();
            assertThat(transaction.isFraud()).isFalse();
            assertThat(event.timestamp()).isEqualTo(DAY);
            assertThat(event.amount()).isGreaterThanOrEqualTo(BigDecimal.ONE);
            assertThat(event.amount().scale()).isEqualTo(2);
            assertThat(event.timeZone()).isEqualTo(cities.get(event.city()).timeZone().getId());
            assertThat(compromisedCards).doesNotContain(event.cardId());
            assertThat(fraudMerchants).doesNotContain(event.merchantId());
        });
    }

    @Test
    void everyScenarioIsGeneratedAndUsesTheMatchingCardsAndMerchants() {
        List<LabeledTransaction> all = runFraudOnly(NIGHT, 600);

        assertThat(all.stream().map(LabeledTransaction::scenario).filter(s -> s != null).distinct())
                .containsExactlyInAnyOrder(FraudScenario.values());
        assertThat(only(all, FraudScenario.COMPROMISED_CARD))
                .allSatisfy(event -> assertThat(compromisedCards).contains(event.cardId()));
        assertThat(only(all, FraudScenario.FRAUD_MERCHANT))
                .allSatisfy(event -> assertThat(fraudMerchants).contains(event.merchantId()));
    }

    @Test
    void velocityBurstPutsAtLeastTwelvePurchasesOfOneCardWithinTwoMinutes() {
        Map<String, List<TransactionEvent>> burstsByCard = only(runFraudOnly(DAY, 300), FraudScenario.VELOCITY_BURST)
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
    void clonedCardIsUsedFarFromHomeShortlyAfterItsOwnerPaidThere() {
        List<LabeledTransaction> all = runFraudOnly(DAY, 300);
        Map<String, TransactionEvent> ownerPurchases = all.stream()
                .filter(transaction -> !transaction.isFraud())
                .map(LabeledTransaction::event)
                .collect(Collectors.toMap(TransactionEvent::cardId, Function.identity(), (a, b) -> a));

        assertThat(only(all, FraudScenario.CLONED_CARD_TRAVEL)).isNotEmpty().allSatisfy(clone -> {
            TransactionEvent owner = ownerPurchases.get(clone.cardId());
            City home = cards.get(clone.cardId()).owner().homeCity();
            assertThat(owner.city()).isEqualTo(home.name());
            assertThat(cities.get(clone.city()).distanceKm(home)).isGreaterThanOrEqualTo(1_500);
            assertThat(Duration.between(owner.timestamp(), clone.timestamp()))
                    .isBetween(Duration.ofMinutes(5), Duration.ofMinutes(30));
        });
    }

    @Test
    void accountTakeoverSpendsManyTimesTheUsualBillOnValuables() {
        Set<String> valuables = mccs(MerchantCategory.HIGH_VALUE);

        assertThat(only(runFraudOnly(DAY, 300), FraudScenario.ACCOUNT_TAKEOVER)).isNotEmpty().allSatisfy(event -> {
            double typical = cards.get(event.cardId()).owner().typicalAmount();
            assertThat(event.amount().doubleValue()).isBetween(50 * typical - 0.01, 150 * typical + 0.01);
            assertThat(valuables).contains(event.mcc());
        });
    }

    @Test
    void nightCashOutHappensAtNightLocalTimeInCashOutCategories() {
        Set<String> cashOut = mccs(MerchantCategory.CASH_OUT);

        assertThat(only(runFraudOnly(NIGHT, 300), FraudScenario.NIGHT_CASH_OUT)).isNotEmpty().allSatisfy(event -> {
            assertThat(LocalTime.ofInstant(event.timestamp(), ZoneId.of(event.timeZone())).getHour()).isLessThan(5);
            assertThat(cashOut).contains(event.mcc());
            assertThat(event.amount()).isBetween(new BigDecimal("20000"), new BigDecimal("80000"));
        });
        assertThat(only(runFraudOnly(DAY, 300), FraudScenario.NIGHT_CASH_OUT)).isEmpty();
    }

    @Test
    void plannedPurchasesAreReleasedOnlyWhenTheyAreDue() {
        TransactionSimulator simulator = new TransactionSimulator(world, new Random(3), 1.0);

        List<LabeledTransaction> immediate = simulator.next(DAY, 50);
        List<LabeledTransaction> afterOneSecond = simulator.next(DAY.plusSeconds(1), 0);
        List<LabeledTransaction> afterThreeMinutes = simulator.next(DAY.plusSeconds(180), 0);
        List<LabeledTransaction> afterHalfAnHour = simulator.next(DAY.plus(Duration.ofMinutes(30)), 0);

        assertThat(immediate).hasSize(50)
                .allSatisfy(transaction -> assertThat(transaction.event().timestamp()).isEqualTo(DAY));
        // The next step of a burst comes no sooner than two seconds after the previous one.
        assertThat(afterOneSecond).isEmpty();
        assertThat(afterThreeMinutes).isNotEmpty()
                .allSatisfy(transaction -> assertThat(transaction.scenario()).isEqualTo(FraudScenario.VELOCITY_BURST));
        assertThat(afterHalfAnHour).isNotEmpty()
                .allSatisfy(transaction -> assertThat(transaction.scenario()).isEqualTo(FraudScenario.CLONED_CARD_TRAVEL));
        assertThat(simulator.next(DAY.plus(Duration.ofHours(1)), 0)).isEmpty();
    }

    /** Starts {@code scenarios} fraud scenarios at once and plays them to the end. */
    private List<LabeledTransaction> runFraudOnly(Instant start, int scenarios) {
        TransactionSimulator simulator = new TransactionSimulator(world, new Random(3), 1.0);
        List<LabeledTransaction> all = new ArrayList<>(simulator.next(start, scenarios));
        all.addAll(simulator.next(start.plus(Duration.ofHours(1)), 0));
        return all;
    }

    private static List<TransactionEvent> only(List<LabeledTransaction> transactions, FraudScenario scenario) {
        return transactions.stream()
                .filter(transaction -> transaction.scenario() == scenario)
                .map(LabeledTransaction::event)
                .toList();
    }

    private static Set<String> mccs(Set<MerchantCategory> categories) {
        return categories.stream().map(MerchantCategory::mcc).collect(Collectors.toSet());
    }

    private static <T> Set<String> ids(List<T> items, Function<T, String> id) {
        return items.stream().map(id).collect(Collectors.toSet());
    }
}
