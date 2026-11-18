package com.antifraud.service.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoTest {

    @Test
    void distanceBetweenMoscowAndNovosibirskMatchesTheKnownValue() {
        // About 2 810 km along a great circle.
        assertThat(Geo.distanceKm(55.7558, 37.6173, 55.0084, 82.9357)).isCloseTo(2_810, within(15.0));
    }

    @Test
    void distanceBetweenMoscowAndSaintPetersburgMatchesTheKnownValue() {
        assertThat(Geo.distanceKm(55.7558, 37.6173, 59.9311, 30.3609)).isCloseTo(634, within(5.0));
    }

    @Test
    void distanceIsSymmetricAndZeroForTheSamePoint() {
        double there = Geo.distanceKm(43.1155, 131.8855, 54.7104, 20.4522);
        double back = Geo.distanceKm(54.7104, 20.4522, 43.1155, 131.8855);

        assertThat(there).isCloseTo(back, within(1e-9));
        assertThat(Geo.distanceKm(55.7558, 37.6173, 55.7558, 37.6173)).isZero();
    }

    @Test
    void antipodesAreHalfTheCircumferenceApart() {
        assertThat(Geo.distanceKm(0, 0, 0, 180)).isCloseTo(Math.PI * 6371.0088, within(1e-6));
    }
}
