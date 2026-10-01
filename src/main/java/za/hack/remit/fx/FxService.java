package za.hack.remit.fx;

import java.math.BigDecimal;

public interface FxService {
    /** Freeze the current rate for a customer. */
    RateLock lockRate(String from, String to);

    /** Our current all-in rate (rand per US$). Used by the demo's live rate display. */
    BigDecimal currentRate(String from, String to);

    /** The current real/mid-market rate (rand per US$), before our margin. */
    BigDecimal midMarketRate(String from, String to);
}