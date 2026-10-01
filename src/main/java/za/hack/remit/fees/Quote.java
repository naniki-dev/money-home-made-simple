package za.hack.remit.fees;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Everything the customer sees and everything the ledger saves. Built once, copied everywhere.
 *
 * @param amountZar        what Thandi wants to send
 * @param feeZar           our fee, in rand
 * @param totalZar         what Thandi pays (amount + fee)
 * @param rate             our rate, rand per US$1
 * @param midMarketRate    the real market rate, for transparency
 * @param receiverGetsUsd  what Mama receives
 * @param rateExpiresAt    when the locked rate runs out
 */
public record Quote(
        BigDecimal amountZar,
        BigDecimal feeZar,
        BigDecimal totalZar,
        BigDecimal rate,
        BigDecimal midMarketRate,
        BigDecimal receiverGetsUsd,
        Instant rateExpiresAt) {
}