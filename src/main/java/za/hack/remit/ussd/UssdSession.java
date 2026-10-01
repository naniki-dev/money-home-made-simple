package za.hack.remit.ussd;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

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

    // ===================== P2 additions: what UssdController / store / screens call =====================
    private String msisdn;
    private String sessionId;
    private ScreenId current = ScreenId.LANGUAGE;
    private final Map<String, String> data = new HashMap<>();
    private String errorKey;
    private Object[] errorArgs = new Object[0];
    private int errorCount;

    public UssdSession() { }                                   // keeps `new UssdSession()` working
    public UssdSession(String msisdn, String sessionId) { this.msisdn = msisdn; this.sessionId = sessionId; }

    public String msisdn() { return msisdn; }
    public String sessionId() { return sessionId; }
    public void setSessionId(String id) { this.sessionId = id; }

    /** Language to render with; English until the user picks one. (Reads your `lang` field.) */
    public String lang() { return lang == null ? "en" : lang; }
    public boolean hasLanguage() { return lang != null; }
    public void setLanguage(String l) { this.lang = l; }

    public ScreenId current() { return current; }
    public void setCurrent(ScreenId id) { this.current = id; }

    public void put(String k, String v) { data.put(k, v); }
    public String get(String k) { return data.get(k); }

    public void fail(String key, Object... args) { errorKey = key; errorArgs = args; }
    public void clearError() { errorKey = null; errorArgs = new Object[0]; }
    public String errorKey() { return errorKey; }
    public Object[] errorArgs() { return errorArgs; }
    public int bumpErrors() { return ++errorCount; }
    public void resetErrorCount() { errorCount = 0; }

    public void touch() { updatedAt = System.currentTimeMillis(); }             // uses your updatedAt
    public Instant lastActive() { return Instant.ofEpochMilli(updatedAt); }

    /** Wipe the in-progress transfer but keep the language (and your saved recipient). */
    public void resetJourney() {
        data.clear();
        clearDraft();
        clearError();
        errorCount = 0;
        current = lang == null ? ScreenId.LANGUAGE : ScreenId.MAIN_MENU;
        step = lang == null ? Step.LANG : Step.MENU;
    }
}