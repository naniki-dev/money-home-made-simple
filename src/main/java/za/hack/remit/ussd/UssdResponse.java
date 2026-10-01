package za.hack.remit.ussd;

/** CON = session continues, END = session closes. Most gateways cap text at ~182 chars. */
public record UssdResponse(String text, boolean end) {
    public static final int MAX_CHARS = 182;

    public static UssdResponse con(String text) { return new UssdResponse(text, false); }
    public static UssdResponse end(String text) { return new UssdResponse(text, true); }

    public boolean fits() { return text.length() <= MAX_CHARS; }

    /** Africa's Talking style wire format. */
    public String toGatewayString() { return (end ? "END " : "CON ") + text; }
}
