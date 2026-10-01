package za.hack.remit.fees;

import org.junit.jupiter.api.Test;
import za.hack.remit.fx.RateLock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class DefaultQuoteCalculatorTest {

    private final Instant now = Instant.parse("2026-10-01T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final DefaultQuoteCalculator calc = new DefaultQuoteCalculator(clock);
    private final RateLock goodLock =
            new RateLock(new BigDecimal("17.80"), new BigDecimal("17.62"), now.plus(Duration.ofMinutes(10)));

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private String errorFor(String amount, RateLock lock) {
        return assertThrows(QuoteException.class, () -> calc.quote(bd(amount), lock)).messageKey();
    }

    @Test
    void r500_example_matches_the_screen() {
        Quote q = calc.quote(bd("500"), goodLock);
        assertEquals(bd("25.00"), q.feeZar());
        assertEquals(bd("525.00"), q.totalZar());
        assertEquals(bd("28.09"), q.receiverGetsUsd());   // 500 / 17.80 = 28.0898...
        assertEquals(bd("17.80"), q.rate());
        assertEquals(bd("17.62"), q.midMarketRate());
    }

    @Test
    void total_is_always_amount_plus_fee() {
        for (String a : new String[]{"50", "99.99", "333.33", "1000", "5000"}) {
            Quote q = calc.quote(bd(a), goodLock);
            assertEquals(q.amountZar().add(q.feeZar()), q.totalZar());
        }
    }

    @Test
    void small_amounts_pay_the_minimum_fee() {
        assertEquals(bd("15.00"), calc.quote(bd("100"), goodLock).feeZar());   // 5% = 5, raised to 15
    }

    @Test
    void big_amounts_pay_at_most_the_maximum_fee() {
        assertEquals(bd("100.00"), calc.quote(bd("5000"), goodLock).feeZar()); // 5% = 250, capped at 100
    }

    @Test
    void rejects_bad_amounts() {
        assertEquals(QuoteException.MIN_AMOUNT, errorFor("49.99", goodLock));
        assertEquals(QuoteException.MAX_AMOUNT, errorFor("5000.01", goodLock));
        assertEquals(QuoteException.INVALID_AMOUNT, errorFor("0", goodLock));
        assertEquals(QuoteException.INVALID_AMOUNT, errorFor("-20", goodLock));
        assertEquals(QuoteException.INVALID_AMOUNT,
                assertThrows(QuoteException.class, () -> calc.quote(null, goodLock)).messageKey());
    }

    @Test
    void rejects_expired_lock() {
        RateLock old = new RateLock(bd("17.80"), bd("17.62"), now.minusSeconds(1));
        assertEquals(QuoteException.RATE_EXPIRED, errorFor("500", old));
    }

    @Test
    void quote_uses_the_locked_rate_not_the_live_one() {
        RateLock other = new RateLock(bd("18.50"), bd("18.32"), now.plus(Duration.ofMinutes(10)));
        assertEquals(bd("28.09"), calc.quote(bd("500"), goodLock).receiverGetsUsd());
        assertEquals(bd("27.03"), calc.quote(bd("500"), other).receiverGetsUsd());   // 500 / 18.50 = 27.027
    }
}