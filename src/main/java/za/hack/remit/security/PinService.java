package za.hack.remit.security;

public interface PinService {
    boolean verify(String msisdn, String pin);
    boolean isLocked(String msisdn);
}
