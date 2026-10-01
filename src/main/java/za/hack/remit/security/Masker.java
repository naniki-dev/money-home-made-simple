package za.hack.remit.security;

public final class Masker {
    private Masker() {}
    /** +263771234567 -> ***4567. Use for screens AND logs. */
    public static String msisdn(String m) {
        if (m == null || m.length() < 4) return "***";
        return "***" + m.substring(m.length() - 4);
    }
}
