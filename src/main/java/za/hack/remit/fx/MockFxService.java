package za.hack.remit.fx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pretend currency market for ZAR -> USD.
 *  1. Start from the real rate (fetched once from a free API), or a fallback if that fails.
 *  2. Every few seconds the rate wobbles a little, never more than 2% away from the start.
 *  3. Our customer rate = market rate + a small, openly disclosed margin.
 *  4. lockRate() freezes the rate for a fixed time.
 */
public class MockFxService implements FxService, AutoCloseable {

    public static final BigDecimal FALLBACK_BASE = new BigDecimal("17.80");
    public static final String RATE_API_URL = "https://open.er-api.com/v6/latest/USD";

    private static final BigDecimal MAX_DRIFT = new BigDecimal("0.02");   // +/- 2% from base
    private static final BigDecimal MAX_STEP = new BigDecimal("0.002");   // up to 0.2% per tick
    private static final Pattern ZAR_PATTERN = Pattern.compile("\"ZAR\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

    private final Clock clock;
    private final Random random;
    private final Duration lockDuration;
    private final BigDecimal margin;      // e.g. 0.01 = 1% on top of the market rate
    private ScheduledExecutorService scheduler;

    private BigDecimal base;              // guarded by "this"
    private BigDecimal live;              // guarded by "this"

    /** Normal use: 10 minute lock, 1% margin. Call start() afterwards. */
    public MockFxService() {
        this(FALLBACK_BASE, Clock.systemUTC(), new Random(), Duration.ofMinutes(10), new BigDecimal("0.01"));
    }

    /** Full control, used by tests (fixed base, fake clock, seeded random). */
    public MockFxService(BigDecimal base, Clock clock, Random random, Duration lockDuration, BigDecimal margin) {
        this.base = base;
        this.live = base;
        this.clock = clock;
        this.random = random;
        this.lockDuration = lockDuration;
        this.margin = margin;
    }

    // ---------- lifecycle ----------

    /** Fetch the real base rate once, then start wobbling. Safe to call: it never throws. */
    public void start() {
        setBase(fetchRealRateOrFallback());
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fx-ticker");
            t.setDaemon(true);   // does not stop the app from exiting
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, 3, 3, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> setBase(fetchRealRateOrFallback()), 1, 1, TimeUnit.HOURS);
    }

    @Override
    public void close() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    // ---------- the wobble ----------

    /** One small random move, kept inside +/-2% of the base. Public so tests can drive it. */
    public synchronized void tick() {
        double r = random.nextDouble() * 2 - 1;                       // -1 .. +1
        BigDecimal step = MAX_STEP.multiply(BigDecimal.valueOf(r));
        BigDecimal next = live.multiply(BigDecimal.ONE.add(step));
        BigDecimal lower = base.multiply(BigDecimal.ONE.subtract(MAX_DRIFT));
        BigDecimal upper = base.multiply(BigDecimal.ONE.add(MAX_DRIFT));
        live = next.max(lower).min(upper).setScale(4, RoundingMode.HALF_UP);
    }

    private synchronized void setBase(BigDecimal newBase) {
        this.base = newBase;
        this.live = newBase;
    }

    // ---------- FxService ----------

    @Override
    public synchronized BigDecimal midMarketRate(String from, String to) {
        requireZarToUsd(from, to);
        return live.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public synchronized BigDecimal currentRate(String from, String to) {
        requireZarToUsd(from, to);
        return customerRate(live);
    }

    @Override
    public synchronized RateLock lockRate(String from, String to) {
        requireZarToUsd(from, to);
        Instant expires = Instant.now(clock).plus(lockDuration);
        return new RateLock(customerRate(live), live.setScale(2, RoundingMode.HALF_UP), expires);
    }

    /** Market rate plus margin, rounded to 2 decimals so the screen and the maths use the SAME number. */
    private BigDecimal customerRate(BigDecimal mid) {
        return mid.multiply(BigDecimal.ONE.add(margin)).setScale(2, RoundingMode.HALF_UP);
    }

    private static void requireZarToUsd(String from, String to) {
        if (!"ZAR".equalsIgnoreCase(from) || !"USD".equalsIgnoreCase(to)) {
            throw new IllegalArgumentException("Only ZAR to USD is supported");
        }
    }

    // ---------- real rate (one call, with a safety net) ----------

    public static BigDecimal fetchRealRateOrFallback() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(RATE_API_URL))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                BigDecimal parsed = parseZarRate(response.body());
                if (parsed != null) return parsed;
            }
        } catch (Exception ignored) {
            // no network, slow API, bad JSON: fall through to the fallback
        }
        return FALLBACK_BASE;
    }

    /** Pulls "ZAR": 17.63 out of the API's JSON. Returns null if missing or silly. */
    static BigDecimal parseZarRate(String json) {
        Matcher m = ZAR_PATTERN.matcher(json);
        if (!m.find()) return null;
        BigDecimal rate = new BigDecimal(m.group(1));
        boolean sensible = rate.compareTo(BigDecimal.valueOf(5)) > 0 && rate.compareTo(BigDecimal.valueOf(50)) < 0;
        return sensible ? rate : null;
    }
}