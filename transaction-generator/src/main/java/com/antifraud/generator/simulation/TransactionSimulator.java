package com.antifraud.generator.simulation;

import com.antifraud.contracts.TransactionEvent;
import com.antifraud.generator.world.Card;
import com.antifraud.generator.world.City;
import com.antifraud.generator.world.Merchant;
import com.antifraud.generator.world.MerchantCategory;
import com.antifraud.generator.world.World;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Produces the stream of purchases: honest traffic with fraud scenarios mixed in.
 *
 * <p>The simulator has no notion of wall-clock time. The caller passes the current instant on
 * every step, which makes the stream reproducible in tests. A scenario that unfolds over time
 * (a burst of purchases) plans its later steps, and they are released once they are due.
 *
 * <p>Not thread-safe: it is meant to be driven by a single thread.
 */
public final class TransactionSimulator {

    private static final String CURRENCY = "RUB";
    /** A cloned card turns up at least this far from home. */
    private static final double CLONE_MIN_DISTANCE_KM = 1_500;
    /** Local hours considered night by the night cash-out scenario: from midnight to 5 a.m. */
    private static final int NIGHT_END_HOUR = 5;

    private final World world;
    private final RandomGenerator random;
    private final double fraudProbability;
    private final List<FraudScenario> scenarios;
    private final PriorityQueue<PlannedPurchase> planned =
            new PriorityQueue<>(Comparator.comparing(PlannedPurchase::at));

    /**
     * @param fraudProbability chance that a generated purchase starts a fraud scenario instead of
     *                         being an honest one
     */
    public TransactionSimulator(World world, RandomGenerator random, double fraudProbability) {
        this.world = world;
        this.random = random;
        this.fraudProbability = fraudProbability;
        this.scenarios = availableScenarios(world);
    }

    /**
     * Returns everything that happens at {@code now}: the planned fraud steps that are due,
     * followed by {@code count} new purchases.
     */
    public List<LabeledTransaction> next(Instant now, int count) {
        List<LabeledTransaction> batch = new ArrayList<>(count + 4);
        while (!planned.isEmpty() && !planned.peek().at().isAfter(now)) {
            batch.add(planned.poll().toTransaction());
        }
        for (int i = 0; i < count; i++) {
            boolean fraud = random.nextDouble() < fraudProbability;
            batch.add(fraud ? startFraud(now) : honestPurchase(now));
        }
        return batch;
    }

    private LabeledTransaction honestPurchase(Instant now) {
        return honestPurchase(pick(world.activeCards()), now);
    }

    private LabeledTransaction honestPurchase(Card card, Instant now) {
        Merchant merchant = pick(world.merchantsIn(card.owner().homeCity()));
        // Log-normal amounts: most purchases are near the customer's usual bill, a few are much larger.
        double amount = card.owner().typicalAmount()
                * merchant.category().amountFactor()
                * Math.exp(0.5 * random.nextGaussian());
        return new PlannedPurchase(now, card, merchant, money(amount), null).toTransaction();
    }

    private LabeledTransaction startFraud(Instant now) {
        List<City> nightCities = citiesAtNightWithCashOutMerchants(now);
        FraudScenario scenario = pick(nightCities.isEmpty()
                ? scenarios.stream().filter(s -> s != FraudScenario.NIGHT_CASH_OUT).toList()
                : scenarios);
        return switch (scenario) {
            case VELOCITY_BURST -> velocityBurst(now);
            case COMPROMISED_CARD -> new PlannedPurchase(now, pick(world.compromisedCards()),
                    pick(world.merchants()), money(between(3_000, 60_000)),
                    FraudScenario.COMPROMISED_CARD).toTransaction();
            case FRAUD_MERCHANT -> new PlannedPurchase(now, pick(world.activeCards()),
                    pick(world.fraudMerchants()), money(between(1_000, 30_000)),
                    FraudScenario.FRAUD_MERCHANT).toTransaction();
            case CLONED_CARD_TRAVEL -> clonedCardTravel(now);
            case ACCOUNT_TAKEOVER -> accountTakeover(now);
            case NIGHT_CASH_OUT -> nightCashOut(pick(nightCities), now);
        };
    }

    /**
     * Card testing: 12 to 20 small purchases, 2 to 6 seconds apart, so the whole burst fits into
     * two minutes. The first purchase happens right away, the rest are planned.
     */
    private LabeledTransaction velocityBurst(Instant now) {
        Card card = pick(world.activeCards());
        List<Merchant> merchants = world.merchantsIn(pick(City.ALL));
        int purchases = 12 + random.nextInt(9);

        LabeledTransaction first = null;
        Instant at = now;
        for (int i = 0; i < purchases; i++) {
            PlannedPurchase purchase = new PlannedPurchase(at, card, pick(merchants),
                    money(between(50, 1_000)), FraudScenario.VELOCITY_BURST);
            if (i == 0) {
                first = purchase.toTransaction();
            } else {
                planned.add(purchase);
            }
            at = at.plusMillis(2_000 + random.nextInt(4_000));
        }
        return first;
    }

    /**
     * The owner pays at home now, and 5 to 30 minutes later a clone of the card is used at least
     * 1 500 km away. The owner's purchase is honest; only the clone's is labelled.
     */
    private LabeledTransaction clonedCardTravel(Instant now) {
        Card card = pick(world.activeCards());
        City home = card.owner().homeCity();
        List<City> farAway = City.ALL.stream().filter(city -> city.distanceKm(home) >= CLONE_MIN_DISTANCE_KM).toList();
        Instant later = now.plus(Duration.ofMinutes(5 + random.nextInt(26)));
        planned.add(new PlannedPurchase(later, card, pick(world.merchantsIn(pick(farAway))),
                money(between(3_000, 40_000)), FraudScenario.CLONED_CARD_TRAVEL));
        return honestPurchase(card, now);
    }

    /** Electronics or jewelry in the owner's city for 50 to 150 times the usual bill. */
    private LabeledTransaction accountTakeover(Instant now) {
        Card card = pick(world.activeCards());
        City home = card.owner().homeCity();
        List<Merchant> merchants = world.merchantsIn(home, MerchantCategory.HIGH_VALUE);
        Merchant merchant = merchants.isEmpty()
                ? pick(world.merchants().stream()
                        .filter(m -> MerchantCategory.HIGH_VALUE.contains(m.category())).toList())
                : pick(merchants);
        double amount = card.owner().typicalAmount() * between(50, 150);
        return new PlannedPurchase(now, card, merchant, money(amount), FraudScenario.ACCOUNT_TAKEOVER).toTransaction();
    }

    /** 20 000 to 80 000 RUB in a cash-out category of a city where it is night now. */
    private LabeledTransaction nightCashOut(City city, Instant now) {
        Merchant merchant = pick(world.merchantsIn(city, MerchantCategory.CASH_OUT));
        return new PlannedPurchase(now, pick(world.activeCards()), merchant, money(between(20_000, 80_000)),
                FraudScenario.NIGHT_CASH_OUT).toTransaction();
    }

    private List<City> citiesAtNightWithCashOutMerchants(Instant now) {
        return City.ALL.stream()
                .filter(city -> LocalTime.ofInstant(now, city.timeZone()).getHour() < NIGHT_END_HOUR)
                .filter(city -> !world.merchantsIn(city, MerchantCategory.CASH_OUT).isEmpty())
                .toList();
    }

    private static List<FraudScenario> availableScenarios(World world) {
        Set<FraudScenario> available = EnumSet.allOf(FraudScenario.class);
        if (world.compromisedCards().isEmpty()) {
            available.remove(FraudScenario.COMPROMISED_CARD);
        }
        if (world.fraudMerchants().isEmpty()) {
            available.remove(FraudScenario.FRAUD_MERCHANT);
        }
        if (world.merchants().stream().noneMatch(m -> MerchantCategory.HIGH_VALUE.contains(m.category()))) {
            available.remove(FraudScenario.ACCOUNT_TAKEOVER);
        }
        return List.copyOf(available);
    }

    private <T> T pick(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }

    private double between(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private static BigDecimal money(double amount) {
        return BigDecimal.valueOf(Math.max(amount, 1.0)).setScale(2, RoundingMode.HALF_UP);
    }

    private record PlannedPurchase(Instant at, Card card, Merchant merchant, BigDecimal amount,
                                   FraudScenario scenario) {

        LabeledTransaction toTransaction() {
            City city = merchant.city();
            // The id is random rather than seeded: it must stay unique across generator restarts.
            TransactionEvent event = new TransactionEvent(
                    UUID.randomUUID(),
                    card.id(),
                    card.owner().id(),
                    amount,
                    CURRENCY,
                    merchant.id(),
                    merchant.category().mcc(),
                    city.name(),
                    city.latitude(),
                    city.longitude(),
                    at,
                    city.timeZone().getId());
            return new LabeledTransaction(event, scenario);
        }
    }
}
