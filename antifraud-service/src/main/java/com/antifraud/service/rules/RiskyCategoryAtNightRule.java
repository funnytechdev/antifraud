package com.antifraud.service.rules;

import com.antifraud.contracts.TransactionEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fires on a large purchase made at night in a category that fraudsters favour: money transfers,
 * jewelry, quasi-cash, gambling. Each of the three conditions alone is ordinary; together they are
 * worth a confirmation.
 *
 * <p>"Night" is the local time of the merchant: 3 a.m. in Vladivostok is 8 p.m. in Moscow. The rule
 * keeps no state, so it never depends on Redis.
 */
@Component
public class RiskyCategoryAtNightRule implements FraudRule<RiskyCategoryAtNightRule.Parameters> {

    public static final String CODE = "RISKY_CATEGORY_AT_NIGHT";

    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "4829", "money transfer",
            "5944", "jewelry",
            "6051", "quasi-cash",
            "7995", "gambling");

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public RuleResult evaluate(TransactionEvent transaction, Parameters parameters) {
        if (!parameters.mccs().contains(transaction.mcc())
                || transaction.amount().compareTo(BigDecimal.valueOf(parameters.minAmount())) < 0) {
            return RuleResult.passed();
        }
        ZoneId zone = zoneOf(transaction);
        LocalTime localTime = LocalTime.ofInstant(transaction.timestamp(), zone);
        if (!parameters.isNight(localTime.getHour())) {
            return RuleResult.passed();
        }
        String category = CATEGORY_NAMES.getOrDefault(transaction.mcc(), "high-risk category");
        return RuleResult.fired(String.format(Locale.ROOT,
                "Purchase of %.2f %s at %02d:%02d local time (%s), %s (MCC %s)",
                transaction.amount(), transaction.currency(), localTime.getHour(), localTime.getMinute(),
                zone.getId(), category, transaction.mcc()));
    }

    /** A malformed time zone must not stop the stream: the rule falls back to UTC. */
    private static ZoneId zoneOf(TransactionEvent transaction) {
        try {
            return ZoneId.of(transaction.timeZone());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }

    /**
     * @param mccs           merchant category codes considered risky
     * @param minAmount      smaller purchases are ignored
     * @param nightStartHour first hour of the night, local time, inclusive
     * @param nightEndHour   hour the night ends, exclusive; may be smaller than the start, e.g. 23 to 5
     */
    public record Parameters(Set<String> mccs, long minAmount, int nightStartHour, int nightEndHour) {

        public Parameters {
            if (mccs == null || mccs.isEmpty() || mccs.stream().anyMatch(mcc -> mcc == null || !mcc.matches("\\d{4}"))) {
                throw new IllegalArgumentException("mccs must be a non-empty list of four-digit codes");
            }
            if (minAmount < 0) {
                throw new IllegalArgumentException("minAmount must not be negative");
            }
            if (!isHour(nightStartHour) || !isHour(nightEndHour) || nightStartHour == nightEndHour) {
                throw new IllegalArgumentException("nightStartHour and nightEndHour must be different hours, 0 to 23");
            }
            mccs = Set.copyOf(mccs);
        }

        boolean isNight(int hour) {
            return nightStartHour < nightEndHour
                    ? hour >= nightStartHour && hour < nightEndHour
                    : hour >= nightStartHour || hour < nightEndHour;
        }

        private static boolean isHour(int hour) {
            return hour >= 0 && hour <= 23;
        }
    }
}
