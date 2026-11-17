package com.antifraud.service.rules;

import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.TransactionEvent;
import com.antifraud.service.blacklist.BlacklistService;
import com.antifraud.service.blacklist.BlacklistType;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Fires when the purchase is made at a merchant from the list of suspicious merchants. */
@Component
public class BlacklistedMerchantRule implements FraudRule {

    public static final String CODE = "BLACKLISTED_MERCHANT";

    private final BlacklistService blacklist;
    private final int score;

    public BlacklistedMerchantRule(BlacklistService blacklist, RuleProperties properties) {
        this.blacklist = blacklist;
        this.score = properties.blacklistedMerchant().score();
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RuleHit> evaluate(TransactionEvent transaction) {
        if (!blacklist.contains(BlacklistType.MERCHANT, transaction.merchantId())) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit(CODE, score,
                "Merchant %s is on the list of suspicious merchants".formatted(transaction.merchantId())));
    }
}
