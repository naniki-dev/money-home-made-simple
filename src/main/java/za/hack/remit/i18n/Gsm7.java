package za.hack.remit.i18n;

/**
 * Validates whether text fits within GSM 03.38 7-bit default alphabet
 * to ensure USSD messages stay under the 160-character single-part limit.
 */
public final class Gsm7 {

    private static final String BASIC_SET =
            "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001bÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
                    "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";

    private static final String EXTENSION_SET = "^{}\\[~]|€";

    private Gsm7() {}

    public static boolean isGsm7(String text) {
        if (text == null) return true;
        for (char c : text.toCharArray()) {
            if (BASIC_SET.indexOf(c) == -1 && EXTENSION_SET.indexOf(c) == -1) {
                return false;
            }
        }
        return true;
    }

    public static int calculateLength(String text) {
        if (text == null) return 0;
        int len = 0;
        for (char c : text.toCharArray()) {
            if (EXTENSION_SET.indexOf(c) != -1) {
                len += 2; // Extension characters count as 2 chars in GSM 7-bit
            } else {
                len += 1;
            }
        }
        return len;
    }
}