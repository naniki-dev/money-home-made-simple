package za.hack.remit.ussd;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory, keyed by MSISDN so a dropped session can be resumed on re-dial. */
public class UssdSessionStore {
    private final ConcurrentHashMap<String, UssdSession> sessions = new ConcurrentHashMap<>();
    private final Duration ttl;

    public UssdSessionStore(Duration ttl) { this.ttl = ttl; }

    public Optional<UssdSession> find(String msisdn) {
        UssdSession s = sessions.get(msisdn);
        if (s == null) return Optional.empty();
        if (s.lastActive().plus(ttl).isBefore(Instant.now())) {
            sessions.remove(msisdn);
            return Optional.empty();
        }
        return Optional.of(s);
    }

    public void save(UssdSession s) { s.touch(); sessions.put(s.msisdn(), s); }
    public void remove(String msisdn) { sessions.remove(msisdn); }
}
