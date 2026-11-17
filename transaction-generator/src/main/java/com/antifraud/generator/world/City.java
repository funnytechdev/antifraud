package com.antifraud.generator.world;

import java.util.List;

/**
 * @param weight relative share of customers and merchants located in the city
 */
public record City(String name, double latitude, double longitude, int weight) {

    public static final List<City> ALL = List.of(
            new City("Moscow", 55.7558, 37.6173, 25),
            new City("Saint Petersburg", 59.9311, 30.3609, 12),
            new City("Novosibirsk", 55.0084, 82.9357, 4),
            new City("Yekaterinburg", 56.8389, 60.6057, 4),
            new City("Kazan", 55.7961, 49.1064, 3),
            new City("Nizhny Novgorod", 56.2965, 43.9361, 3),
            new City("Chelyabinsk", 55.1644, 61.4368, 3),
            new City("Samara", 53.1959, 50.1002, 3),
            new City("Omsk", 54.9885, 73.3242, 3),
            new City("Rostov-on-Don", 47.2357, 39.7015, 3),
            new City("Ufa", 54.7388, 55.9721, 3),
            new City("Krasnoyarsk", 56.0153, 92.8932, 3),
            new City("Vladivostok", 43.1155, 131.8855, 2),
            new City("Kaliningrad", 54.7104, 20.4522, 1),
            new City("Sochi", 43.6028, 39.7342, 1));
}
