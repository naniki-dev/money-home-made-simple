package za.hack.remit.security;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Allows at most `max` requests per `window` for each key (we use the phone number). */
public class SlidingWindowRateLimiter implements RateLimiter {
    private final int max;
    private final long windowMs;
    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    public SlidingWindowRateLimiter(int max, Duration window) { this(max, window, Clock.systemUTC()); }

    public SlidingWindowRateLimiter(int max, Duration window, Clock clock) {
        this.max = max;
        this.windowMs = window.toMillis();
        this.clock = clock;
    }

    @Override public synchronized boolean allow(String key) {
        long now = clock.millis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() >= windowMs) q.pollFirst();
        if (q.size() >= max) return false;
        q.addLast(now);
        if (hits.size() > 10_000) hits.values().removeIf(Deque::isEmpty);   // keep memory bounded
        return true;
    }
}
