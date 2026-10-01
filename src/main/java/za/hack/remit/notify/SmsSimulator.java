package za.hack.remit.notify;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class SmsSimulator {

    public record SmsMessage(Instant sentAt, String toMasked, String text) {}

    private static final int MAX_LENGTH = 160;
    private static final int MAX_KEPT = 50;

    private final Deque<SmsMessage> outbox = new ArrayDeque<>();

    /** "Sends" an SMS. Returns the stored message (with the number masked). */
    public synchronized SmsMessage send(String toPhone, String text) {
        if (toPhone == null || toPhone.isBlank()) {
            throw new IllegalArgumentException("Recipient phone is required");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Message text is required");
        }
        // Remove control characters (keeps line breaks), then check the length.
        String clean = text.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").trim();
        if (clean.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("SMS is longer than " + MAX_LENGTH + " characters");
        }
        SmsMessage sms = new SmsMessage(Instant.now(), mask(toPhone), clean);
        outbox.addFirst(sms);
        while (outbox.size() > MAX_KEPT) {
            outbox.removeLast();
        }
        return sms;
    }

    /** Newest first. Returns a copy so callers can't change the real list. */
    public synchronized List<SmsMessage> recent() {
        return new ArrayList<>(outbox);
    }

    /** "+263771234567" becomes "+263******567". */
    static String mask(String phone) {
        String p = phone.trim();
        if (p.length() <= 7) {
            return "****";
        }
        return p.substring(0, 4) + "*".repeat(p.length() - 7) + p.substring(p.length() - 3);
    }
}