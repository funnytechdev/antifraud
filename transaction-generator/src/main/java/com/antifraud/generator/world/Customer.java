package com.antifraud.generator.world;

/**
 * @param typicalAmount median grocery-level purchase of the customer, in roubles
 */
public record Customer(String id, City homeCity, double typicalAmount) {
}
