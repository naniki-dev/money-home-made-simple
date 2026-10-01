package za.hack.remit.fx;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MockFxServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);

    private MockFxService service(String margin) {
        return new MockFxService(new BigDecimal("17.80"), clock, new Random(42),
                Duration.ofMinutes(10), new BigDecimal(margin));
    }

    @Test
    void rate_never_drifts_more_than_2_percent() {
        MockFxService fx = service("0");
        BigDecimal lower = new BigDecimal("17.80").multiply(new BigDecimal("0.98"));
        BigDecimal upper = new BigDecimal("17.80").multiply(new BigDecimal("1.02"));
        for (int i = 0; i < 10_000; i++) {
            fx.tick();
            BigDecimal mid = fx.midMarketRate("ZAR", "USD");
            assertTrue(mid.compareTo(lower.subtract(new BigDecimal("0.01"))) >= 0, "too low: " + mid);
            assertTrue(mid.compareTo(upper.add(new BigDecimal("0.01"))) <= 0, "too high: " + mid);
        }
    }

    @Test
    void rate_actually_moves() {
        MockFxService fx = service("0");
        BigDecimal first = fx.midMarketRate("ZAR", "USD");
        boolean moved = false;
        for (int i = 0; i < 200 && !moved; i++) {
            fx.tick();
            moved = fx.midMarketRate("ZAR", "USD").compareTo(first) != 0;
        }
        assertTrue(moved);
    }

    @Test
    void lock_stays_frozen_while_live_rate_moves() {
        MockFxService fx = service("0.01");
        RateLock lock = fx.lockRate("ZAR", "USD");
        BigDecimal frozen = lock.rate();
        for (int i = 0; i < 500; i++) fx.tick();
        assertEquals(frozen, lock.rate());
    }

    @Test
    void lock_expires_after_ten_minutes() {
        MockFxService fx = service("0.01");
        RateLock lock = fx.lockRate("ZAR", "USD");
        assertFalse(lock.isExpired(clock));
        Clock later = Clock.fixed(Instant.parse("2026-10-01T10:10:00Z"), ZoneOffset.UTC);
        assertTrue(lock.isExpired(later));
    }

    @Test
    void customer_rate_is_market_rate_plus_margin() {
        MockFxService fx = service("0.01");
        RateLock lock = fx.lockRate("ZAR", "USD");
        assertEquals(new BigDecimal("17.80"), lock.midMarketRate());
        assertEquals(new BigDecimal("17.98"), lock.rate());   // 17.80 * 1.01 = 17.978
    }

    @Test
    void only_zar_to_usd_is_supported() {
        assertThrows(IllegalArgumentException.class, () -> service("0").lockRate("ZAR", "EUR"));
    }

    @Test
    void parses_the_api_json_and_ignores_rubbish() {
        assertEquals(new BigDecimal("17.63"),
                MockFxService.parseZarRate("{\"rates\":{\"USD\":1,\"ZAR\": 17.63,\"ZMW\":26.1}}"));
        assertNull(MockFxService.parseZarRate("{\"rates\":{\"ZAR\": 0.5}}"));
        assertNull(MockFxService.parseZarRate("not json"));
    }
}