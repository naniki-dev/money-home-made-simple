package za.hack.remit.ussd.screens;

import za.hack.remit.fees.QuoteService;
import za.hack.remit.i18n.Messages;
import za.hack.remit.security.PinService;
import za.hack.remit.transfer.QuoteExpiredException;
import za.hack.remit.transfer.TransferService;
import za.hack.remit.ussd.*;

public class PinScreen extends BaseScreen {
    private final QuoteService quotes;
    private final TransferService transfers;
    private final PinService pins;

    public PinScreen(Messages m, QuoteService quotes, TransferService transfers, PinService pins) {
        super(m);
        this.quotes = quotes;
        this.transfers = transfers;
        this.pins = pins;
    }

    @Override public ScreenId id() { return ScreenId.PIN; }
    @Override protected String body(UssdSession s) { return msg(s, "pin.prompt"); }

    @Override public ScreenId handle(UssdSession s, String input) {
        if (pins.isLocked(s.msisdn())) return exit(s, "err.pin_locked");
        if (!InputValidator.isPin(input) || !pins.verify(s.msisdn(), input)) {
            s.fail("err.pin_wrong");                 // never log or store `input`
            return id();
        }
        try {
            // Idempotency: a double-tap / replayed request maps to the same transfer.
            String key = s.sessionId() + ":" + s.get("quoteId");
            String ref = transfers.create(key, s.get("quoteId"), s.msisdn(), s.get("recipient"));
            return exit(s, "done", ref);
        } catch (QuoteExpiredException e) {
            s.fail("err.quote_expired");
            return ScreenId.CONFIRM;                 // ConfirmScreen re-quotes and shows the new numbers
        }
    }

    private ScreenId exit(UssdSession s, String key, String... args) {
        s.put("exitKey", key);
        s.put("exitArg1", args.length > 0 ? args[0] : "");
        return ScreenId.EXIT;
    }
}
