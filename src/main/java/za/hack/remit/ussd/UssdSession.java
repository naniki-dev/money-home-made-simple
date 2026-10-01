package za.hack.remit.ussd;

import java.math.BigDecimal;

/** Keyed by PHONE NUMBER (not sessionId) so a dropped session can be resumed. */
public class UssdSession {
    public enum Step {
        LANG, MENU, RESUME,
        SEND_RECIPIENT, SEND_AMOUNT, SEND_CONFIRM,
        STATUS_REF,
        AUTO_MENU, AUTO_AMOUNT, AUTO_DAY, AUTO_CONFIRM
    }

    public String lang;                  // null until chosen, then remembered
    public Step step = Step.LANG;
    public long updatedAt = System.currentTimeMillis();

    // Draft transfer (survives a dropped session)
    public String recipient;             // saved after first send -> repeat send is fast
    public BigDecimal amount;
    public String draftKey;              // idempotency key, created with the draft

    // Draft monthly plan
    public BigDecimal autoAmount;
    public int autoDay;

    public boolean hasDraft() { return amount != null && draftKey != null; }

    public void clearDraft() { amount = null; draftKey = null; autoAmount = null; autoDay = 0; }

    public boolean isFresh(long maxAgeMs) { return System.currentTimeMillis() - updatedAt < maxAgeMs; }
}