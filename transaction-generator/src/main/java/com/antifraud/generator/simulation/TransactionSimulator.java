package com.antifraud.generator.simulation;

import com.antifraud.contracts.TransactionEvent;
import com.antifraud.generator.world.Card;
import com.antifraud.generator.world.City;
import com.antifraud.generator.world.Merchant;
import com.antifraud.generator.world.World;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
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
            boolean fraud = !scenarios.isEmpty() && random.nextDouble() < fraudProbability;
            batch.add(fraud ? startFraud(now) : honestPurchase(now));
        }
        return batch;
    }

    private LabeledTransaction honestPurchase(Instant now) {
        Card card = pick(world.activeCards());
        Merchant merchant = pick(world.merchantsIn(card.owner().homeCity()));
        // Log-normal amounts: most purchases are near the customer's usual bill, a few are much larger.
        double amount = card.owner().typicalAmount()
                * merchant.category().amountFactor()
                * Math.exp(0.5 * random.nextGaussian());
        return new PlannedPurchase(now, card, merchant, money(amount), null).toTransaction();
    }

    private LabeledTransaction startFraud(Instant now) {
        return switch (pick(scenarios)) {
            case VELOCITY_BURST -> velocityBurst(now);
            case COMPROMISED_CARD -> new PlannedPurchase(now, pick(world.compromisedCards()),
                    pick(world.merchants()), money(between(3_000, 60_000)),
                    FraudScenario.COMPROMISED_CARD).toTransaction();
            case FRAUD_MERCHANT -> new PlannedPurchase(now, pick(world.activeCards()),
                    pick(world.fraudMerchants()), money(between(1_000, 30_000)),
                    FraudScenario.FRAUD_MERCHANT).toTransaction();
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

    private static List<FraudScenario> availableScenarios(World world) {
        List<FraudScenario> available = new ArrayList<>();
        available.add(FraudScenario.VELOCITY_BURST);
        if (!world.compromisedCards().isEmpty()) {
            available.add(FraudScenario.COMPROMISED_CARD);
        }
        if (!world.fraudMerchants().isEmpty()) {
            available.add(FraudScenario.FRAUD_MERCHANT);
        }
        return available;
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
                    at);
            return new LabeledTransaction(event, scenario);
        }
    }
}
