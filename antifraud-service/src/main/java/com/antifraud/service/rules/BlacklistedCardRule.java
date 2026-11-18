package com.antifraud.service.rules;

import com.antifraud.contracts.TransactionEvent;
import com.antifraud.service.blacklist.BlacklistService;
import com.antifraud.service.blacklist.BlacklistType;
import org.springframework.stereotype.Component;

/** Fires when the card is on the list of compromised cards. */
@Component
public class BlacklistedCardRule implements FraudRule<NoParameters> {

    public static final String CODE = "BLACKLISTED_CARD";

    private final BlacklistService blacklist;

    public BlacklistedCardRule(BlacklistService blacklist) {
        this.blacklist = blacklist;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Class<NoParameters> parametersType() {
        return NoParameters.class;
    }

    @Override
    public RuleResult evaluate(TransactionEvent transaction, NoParameters parameters) {
        if (!blacklist.contains(BlacklistType.CARD, transaction.cardId())) {
            return RuleResult.passed();
        }
        return RuleResult.fired("The card is on the list of compromised cards");
    }
}
