package za.hack.remit.security;

public interface PinService {
    boolean verify(String msisdn, String pin);
    boolean isLocked(String msisdn);

    /** Has this phone set a PIN yet? The PIN screen shows "create a PIN" when not. */
    default boolean isSet(String msisdn) { return true; }
}
