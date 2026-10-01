package za.hack.remit.fx;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * A rate frozen for one customer.
 *
 * @param rate           OUR all-in rate: rand per 1 US$ (what the customer actually gets)
 * @param midMarketRate  the "real" market rate at the moment of locking (for transparency)
 * @param expiresAt      after this moment the lock is no longer valid
 */
public record RateLock(BigDecimal rate, BigDecimal midMarketRate, Instant expiresAt) {

    public boolean isExpired(Clock clock) {
        return !Instant.now(clock).isBefore(expiresAt);
    }
}