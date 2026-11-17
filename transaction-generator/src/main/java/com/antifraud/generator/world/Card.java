package com.antifraud.generator.world;

/**
 * @param id card token; the generator never produces real card numbers
 */
public record Card(String id, Customer owner) {
}
