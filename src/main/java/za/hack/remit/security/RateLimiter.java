package za.hack.remit.security;

public interface RateLimiter {
    boolean allow(String key);
}
