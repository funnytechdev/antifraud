package com.antifraud.service.rules;

import com.antifraud.contracts.TransactionEvent;
import com.antifraud.service.blacklist.BlacklistService;
import com.antifraud.service.blacklist.BlacklistType;
import org.springframework.stereotype.Component;

/** Fires when the purchase is made at a merchant from the list of suspicious merchants. */
@Component
public class BlacklistedMerchantRule implements FraudRule<NoParameters> {

    public static final String CODE = "BLACKLISTED_MERCHANT";

    private final BlacklistService blacklist;

    public BlacklistedMerchantRule(BlacklistService blacklist) {
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
        if (!blacklist.contains(BlacklistType.MERCHANT, transaction.merchantId())) {
            return RuleResult.passed();
        }
        return RuleResult.fired("Merchant %s is on the list of suspicious merchants"
                .formatted(transaction.merchantId()));
    }
}
