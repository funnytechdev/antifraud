package com.antifraud.generator.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * The simulated population: customers with their cards, and merchants.
 *
 * <p>The world is fully determined by the random seed, so the same seed always yields the same
 * compromised cards and fraudulent merchants. That keeps the blacklist stable between runs.
 */
public final class World {

    private static final List<MerchantCategory> FRAUD_MERCHANT_CATEGORIES = List.of(
            MerchantCategory.MISCELLANEOUS, MerchantCategory.MONEY_TRANSFER, MerchantCategory.QUASI_CASH);

    private final List<Card> activeCards;
    private final List<Card> compromisedCards;
    private final List<Merchant> merchants;
    private final List<Merchant> fraudMerchants;
    private final Map<City, List<Merchant>> merchantsByCity;

    private World(List<Card> activeCards, List<Card> compromisedCards,
                  List<Merchant> merchants, List<Merchant> fraudMerchants) {
        this.activeCards = List.copyOf(activeCards);
        this.compromisedCards = List.copyOf(compromisedCards);
        this.merchants = List.copyOf(merchants);
        this.fraudMerchants = List.copyOf(fraudMerchants);
        this.merchantsByCity = merchants.stream().collect(Collectors.groupingBy(Merchant::city));
    }

    /**
     * @param customers        number of customers; each owns one or two cards
     * @param merchants        number of honest merchants, at least one per city
     * @param compromisedCards number of cards considered stolen; their owners no longer use them
     * @param fraudMerchants   number of merchants run by fraudsters; honest traffic avoids them
     */
    public static World generate(int customers, int merchants, int compromisedCards, int fraudMerchants,
                                 RandomGenerator random) {
        if (merchants < City.ALL.size()) {
            throw new IllegalArgumentException("Need at least %d merchants, one per city".formatted(City.ALL.size()));
        }

        List<Card> cards = new ArrayList<>();
        for (int i = 1; i <= customers; i++) {
            City home = pickWeighted(City.ALL, City::weight, random);
            double typicalAmount = Math.clamp(1500 * Math.exp(0.6 * random.nextGaussian()), 200, 20_000);
            Customer customer = new Customer("cust-%06d".formatted(i), home, typicalAmount);
            int cardCount = random.nextDouble() < 0.3 ? 2 : 1;
            for (int c = 0; c < cardCount; c++) {
                cards.add(new Card("card-%07d".formatted(cards.size() + 1), customer));
            }
        }
        if (compromisedCards >= cards.size()) {
            throw new IllegalArgumentException("Compromised cards must be fewer than all cards");
        }
        int firstCompromised = cards.size() - compromisedCards;

        List<Merchant> honest = new ArrayList<>();
        for (int i = 0; i < merchants; i++) {
            // The first pass guarantees that every city has a merchant, the rest follow city size.
            City city = i < City.ALL.size() ? City.ALL.get(i) : pickWeighted(City.ALL, City::weight, random);
            MerchantCategory category = pickWeighted(List.of(MerchantCategory.values()), MerchantCategory::weight, random);
            honest.add(new Merchant("merch-%05d".formatted(i + 1), category, city));
        }

        List<Merchant> fraudulent = new ArrayList<>();
        for (int i = 0; i < fraudMerchants; i++) {
            MerchantCategory category = FRAUD_MERCHANT_CATEGORIES.get(random.nextInt(FRAUD_MERCHANT_CATEGORIES.size()));
            City city = pickWeighted(City.ALL, City::weight, random);
            fraudulent.add(new Merchant("merch-%05d".formatted(merchants + i + 1), category, city));
        }

        return new World(cards.subList(0, firstCompromised), cards.subList(firstCompromised, cards.size()),
                honest, fraudulent);
    }

    public List<Card> activeCards() {
        return activeCards;
    }

    public List<Card> compromisedCards() {
        return compromisedCards;
    }

    public List<Merchant> merchants() {
        return merchants;
    }

    public List<Merchant> fraudMerchants() {
        return fraudMerchants;
    }

    public List<Merchant> merchantsIn(City city) {
        return merchantsByCity.get(city);
    }

    /** Honest merchants of the given categories in the city; may be empty. */
    public List<Merchant> merchantsIn(City city, Set<MerchantCategory> categories) {
        return merchantsByCity.get(city).stream()
                .filter(merchant -> categories.contains(merchant.category()))
                .toList();
    }

    private static <T> T pickWeighted(List<T> items, ToIntFunction<T> weight, RandomGenerator random) {
        int total = items.stream().mapToInt(weight).sum();
        int roll = random.nextInt(total);
        for (T item : items) {
            roll -= weight.applyAsInt(item);
            if (roll < 0) {
                return item;
            }
        }
        throw new IllegalStateException("Weights must be positive");
    }
}
