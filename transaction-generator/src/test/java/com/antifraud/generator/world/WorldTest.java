package com.antifraud.generator.world;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WorldTest {

    @Test
    void sameSeedProducesTheSameWorld() {
        World first = World.generate(500, 60, 10, 3, new Random(7));
        World second = World.generate(500, 60, 10, 3, new Random(7));

        assertThat(first.activeCards()).isEqualTo(second.activeCards());
        assertThat(first.compromisedCards()).isEqualTo(second.compromisedCards());
        assertThat(first.merchants()).isEqualTo(second.merchants());
        assertThat(first.fraudMerchants()).isEqualTo(second.fraudMerchants());
    }

    @Test
    void compromisedCardsAndFraudMerchantsAreKeptApartFromHonestOnes() {
        World world = World.generate(500, 60, 10, 3, new Random(7));

        assertThat(world.compromisedCards()).hasSize(10).doesNotContainAnyElementsOf(world.activeCards());
        assertThat(world.fraudMerchants()).hasSize(3).doesNotContainAnyElementsOf(world.merchants());
        assertThat(world.activeCards()).hasSizeGreaterThanOrEqualTo(490);
    }

    @Test
    void everyCityHasAtLeastOneHonestMerchant() {
        World world = World.generate(100, City.ALL.size(), 0, 0, new Random(7));

        assertThat(City.ALL).allSatisfy(city -> assertThat(world.merchantsIn(city)).isNotEmpty());
    }

    @Test
    void rejectsFewerMerchantsThanCities() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> World.generate(100, City.ALL.size() - 1, 0, 0, new Random(7)));
    }
}
