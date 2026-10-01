package za.hack.remit.fees;

import za.hack.remit.fx.RateLock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;

/** The ONLY place in the app where money maths happens. */
public class DefaultQuoteCalculator implements QuoteCalculator {

    // ---- The fee rule lives here. Change these numbers and everything follows. ----
    public static final BigDecimal MIN_AMOUNT_ZAR = new BigDecimal("50");
    public static final BigDecimal MAX_AMOUNT_ZAR = new BigDecimal("5000");
    public static final BigDecimal FEE_PERCENT = new BigDecimal("0.05");   // 5% of the amount
    public static final BigDecimal MIN_FEE_ZAR = new BigDecimal("15");
    public static final BigDecimal MAX_FEE_ZAR = new BigDecimal("100");

    private final Clock clock;

    public DefaultQuoteCalculator() {
        this(Clock.systemUTC());
    }

    public DefaultQuoteCalculator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Quote quote(BigDecimal amountZar, RateLock lock) {
        if (amountZar == null || amountZar.signum() <= 0) {
            throw new QuoteException(QuoteException.INVALID_AMOUNT);
        }
        BigDecimal amount = amountZar.setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(MIN_AMOUNT_ZAR) < 0) throw new QuoteException(QuoteException.MIN_AMOUNT);
        if (amount.compareTo(MAX_AMOUNT_ZAR) > 0) throw new QuoteException(QuoteException.MAX_AMOUNT);
        if (lock == null || lock.isExpired(clock)) throw new QuoteException(QuoteException.RATE_EXPIRED);

        BigDecimal fee = amount.multiply(FEE_PERCENT)
                .max(MIN_FEE_ZAR)
                .min(MAX_FEE_ZAR)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = amount.add(fee);
        BigDecimal receiverGets = amount.divide(lock.rate(), 2, RoundingMode.HALF_UP);

        return new Quote(amount, fee, total, lock.rate(), lock.midMarketRate(), receiverGets, lock.expiresAt());
    }
}