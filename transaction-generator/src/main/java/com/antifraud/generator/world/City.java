package com.antifraud.generator.world;

import java.time.ZoneId;
import java.util.List;

/**
 * @param weight   relative share of customers and merchants located in the city
 * @param timeZone local time zone; Russia spans eleven of them
 */
public record City(String name, double latitude, double longitude, ZoneId timeZone, int weight) {

    public static final List<City> ALL = List.of(
            city("Moscow", 55.7558, 37.6173, "Europe/Moscow", 25),
            city("Saint Petersburg", 59.9311, 30.3609, "Europe/Moscow", 12),
            city("Novosibirsk", 55.0084, 82.9357, "Asia/Novosibirsk", 4),
            city("Yekaterinburg", 56.8389, 60.6057, "Asia/Yekaterinburg", 4),
            city("Kazan", 55.7961, 49.1064, "Europe/Moscow", 3),
            city("Nizhny Novgorod", 56.2965, 43.9361, "Europe/Moscow", 3),
            city("Chelyabinsk", 55.1644, 61.4368, "Asia/Yekaterinburg", 3),
            city("Samara", 53.1959, 50.1002, "Europe/Samara", 3),
            city("Omsk", 54.9885, 73.3242, "Asia/Omsk", 3),
            city("Rostov-on-Don", 47.2357, 39.7015, "Europe/Moscow", 3),
            city("Ufa", 54.7388, 55.9721, "Asia/Yekaterinburg", 3),
            city("Krasnoyarsk", 56.0153, 92.8932, "Asia/Krasnoyarsk", 3),
            city("Vladivostok", 43.1155, 131.8855, "Asia/Vladivostok", 2),
            city("Kaliningrad", 54.7104, 20.4522, "Europe/Kaliningrad", 1),
            city("Sochi", 43.6028, 39.7342, "Europe/Moscow", 1));

    private static City city(String name, double latitude, double longitude, String timeZone, int weight) {
        return new City(name, latitude, longitude, ZoneId.of(timeZone), weight);
    }
}
