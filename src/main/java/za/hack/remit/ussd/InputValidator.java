package za.hack.remit.ussd;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure functions, no state. Every method returns empty on bad input, never throws. */
public final class InputValidator {
    private static final Pattern AMOUNT = Pattern.compile("^\\d{1,7}(\\.\\d{1,2})?$");
    private static final Pattern ZW_MOBILE = Pattern.compile("^(?:\\+?263|0)(7\\d{8})$");
    private static final Pattern PIN = Pattern.compile("^\\d{4}$");
    private static final Pattern REFERENCE = Pattern.compile("^[A-Z0-9]{6,16}$");

    private InputValidator() {}

    /** 1..max, digits only. */
    public static Optional<Integer> choice(String in, int max) {
        if (in == null || !in.matches("^\\d{1,2}$")) return Optional.empty();
        int n = Integer.parseInt(in);
        return (n >= 1 && n <= max) ? Optional.of(n) : Optional.empty();
    }

    /** Accepts "500", "500.50", "R500". Rejects commas and anything ambiguous. */
    public static Optional<BigDecimal> amount(String in) {
        if (in == null) return Optional.empty();
        String s = in.trim().replace(" ", "");
        if (s.startsWith("R") || s.startsWith("r")) s = s.substring(1);
        if (!AMOUNT.matcher(s).matches()) return Optional.empty();
        BigDecimal v = new BigDecimal(s);
        return v.signum() > 0 ? Optional.of(v) : Optional.empty();
    }

    /** Normalises 0771234567 / 263771234567 / +263771234567 to +263771234567. */
    public static Optional<String> zimbabweMsisdn(String in) {
        if (in == null) return Optional.empty();
        Matcher m = ZW_MOBILE.matcher(in.trim().replace(" ", ""));
        return m.matches() ? Optional.of("+263" + m.group(1)) : Optional.empty();
    }

    public static boolean isPin(String in) { return in != null && PIN.matcher(in).matches(); }

    public static Optional<String> reference(String in) {
        if (in == null) return Optional.empty();
        String s = in.trim().toUpperCase();
        return REFERENCE.matcher(s).matches() ? Optional.of(s) : Optional.empty();
    }
}
