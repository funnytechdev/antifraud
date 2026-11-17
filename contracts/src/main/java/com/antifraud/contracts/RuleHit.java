package com.antifraud.contracts;

/**
 * One rule that fired for a transaction.
 *
 * @param rule   stable rule code, e.g. {@code VELOCITY}
 * @param score  risk points the rule contributed
 * @param reason human-readable explanation of why the rule fired
 */
public record RuleHit(String rule, int score, String reason) {
}
