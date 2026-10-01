package za.hack.remit.stubs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import za.hack.remit.fees.*;
import za.hack.remit.security.*;
import za.hack.remit.transfer.*;

/** Throwaway implementations so P2 can run end-to-end today. Delete when the real ones land. */
public final class Stubs {
    private Stubs() {}

    public static class StubQuotes implements QuoteService {
        private final Map<String, Quote> quotes = new ConcurrentHashMap<>();
        @Override public Quote quote(BigDecimal zar) {
            BigDecimal fee = zar.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal rate = new BigDecimal("0.0545");
            BigDecimal usd = zar.subtract(fee).multiply(rate).setScale(2, RoundingMode.HALF_DOWN);
            Quote q = new Quote(UUID.randomUUID().toString(), zar.setScale(2), fee, rate, usd,
                    Instant.now().plus(Duration.ofMinutes(5)));
            quotes.put(q.quoteId(), q);
            return q;
        }
        @Override public Optional<Quote> find(String id) { return Optional.ofNullable(id == null ? null : quotes.get(id)); }
    }

    public static class StubTransfers implements TransferService {
        private final Map<String, String> byKey = new ConcurrentHashMap<>();
        @Override public String create(String key, String quoteId, String from, String to) {
            return byKey.computeIfAbsent(key, k -> "TRF" + (100000 + byKey.size()));
        }
        @Override public Optional<String> status(String ref) {
            return byKey.containsValue(ref) ? Optional.of("IN_TRANSIT") : Optional.empty();
        }
    }

    public static class StubPins implements PinService {
        @Override public boolean verify(String msisdn, String pin) { return "1234".equals(pin); }
        @Override public boolean isLocked(String msisdn) { return false; }
    }

    public static final RateLimiter ALLOW_ALL = key -> true;

    public static final LimitPolicy LIMITS = new LimitPolicy() {
        @Override public BigDecimal min() { return new BigDecimal("50"); }
        @Override public BigDecimal max() { return new BigDecimal("5000"); }
    };
}
