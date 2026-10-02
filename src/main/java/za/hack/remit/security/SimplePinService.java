package za.hack.remit.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * In-memory PIN store for the demo.
 *  - PINs are salted and hashed (PBKDF2), never stored or logged in clear.
 *  - 3 wrong tries lock the phone for 15 minutes.
 *  - Demo shortcut: the first PIN a phone enters becomes its PIN. A real system would enrol
 *    the PIN through a verified channel (agent, app, or SIM-bound registration).
 */
public class SimplePinService implements PinService {
    static final int MAX_ATTEMPTS = 3;
    static final Duration LOCK_FOR = Duration.ofMinutes(15);
    private static final int ITERATIONS = 20_000;

    private record Entry(byte[] salt, byte[] hash) {}
    private static final class Attempts { int failures; Instant lockedUntil; }

    private final Map<String, Entry> pins = new ConcurrentHashMap<>();
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public SimplePinService() { this(Clock.systemUTC()); }
    public SimplePinService(Clock clock) { this.clock = clock; }

    @Override public boolean isSet(String msisdn) { return pins.containsKey(msisdn); }

    @Override public boolean isLocked(String msisdn) {
        Attempts a = attempts.get(msisdn);
        if (a == null) return false;
        synchronized (a) {
            if (a.lockedUntil == null) return false;
            if (Instant.now(clock).isBefore(a.lockedUntil)) return true;
            a.lockedUntil = null;               // lock has expired: fresh start
            a.failures = 0;
            return false;
        }
    }

    @Override public boolean verify(String msisdn, String pin) {
        if (msisdn == null || pin == null || !pin.matches("\\d{4}")) return false;
        if (isLocked(msisdn)) return false;
        Entry stored = pins.get(msisdn);
        if (stored == null) {                   // first use: enrol
            set(msisdn, pin);
            return true;
        }
        if (MessageDigest.isEqual(stored.hash(), hash(pin, stored.salt()))) {
            attempts.remove(msisdn);
            return true;
        }
        Attempts a = attempts.computeIfAbsent(msisdn, k -> new Attempts());
        synchronized (a) {
            if (++a.failures >= MAX_ATTEMPTS) a.lockedUntil = Instant.now(clock).plus(LOCK_FOR);
        }
        return false;
    }

    public void set(String msisdn, String pin) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        pins.put(msisdn, new Entry(salt, hash(pin, salt)));
        attempts.remove(msisdn);
    }

    private static byte[] hash(String pin, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PIN hashing unavailable");
        }
    }
}
