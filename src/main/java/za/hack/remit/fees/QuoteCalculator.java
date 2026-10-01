package za.hack.remit.fees;

import za.hack.remit.fx.RateLock;

import java.math.BigDecimal;

public interface QuoteCalculator {
    /** @throws QuoteException with a message key if the amount or the lock is not acceptable */
    Quote quote(BigDecimal amountZar, RateLock lock);
}