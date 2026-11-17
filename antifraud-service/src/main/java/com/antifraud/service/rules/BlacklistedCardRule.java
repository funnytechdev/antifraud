package com.antifraud.service.rules;

import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.TransactionEvent;
import com.antifraud.service.blacklist.BlacklistService;
import com.antifraud.service.blacklist.BlacklistType;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Fires when the card is on the list of compromised cards. */
@Component
public class BlacklistedCardRule implements FraudRule {

    public static final String CODE = "BLACKLISTED_CARD";

    private final BlacklistService blacklist;
    private final int score;

    public BlacklistedCardRule(BlacklistService blacklist, RuleProperties properties) {
        this.blacklist = blacklist;
        this.score = properties.blacklistedCard().score();
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RuleHit> evaluate(TransactionEvent transaction) {
        if (!blacklist.contains(BlacklistType.CARD, transaction.cardId())) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit(CODE, score, "The card is on the list of compromised cards"));
    }
}
