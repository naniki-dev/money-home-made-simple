package za.hack.remit.ussd.screens;

import java.time.Instant;
import za.hack.remit.i18n.Messages;
import za.hack.remit.security.PinService;
import za.hack.remit.ussd.*;

/** Only registered when UssdFactory is given a PinService. */
public class PinScreen extends BaseScreen {
    private final PinService pins;
    private final SendAction send;

    public PinScreen(Messages m, PinService pins, SendAction send) {
        super(m);
        this.pins = pins;
        this.send = send;
    }

    @Override public ScreenId id() { return ScreenId.PIN; }
    @Override protected String body(UssdSession s) { return msg(s, "pin.prompt"); }

    @Override public ScreenId handle(UssdSession s, String input) {
        if (pins.isLocked(s.msisdn())) {
            s.put("exitKey", "err.pin_locked");
            return ScreenId.EXIT;
        }
        if (!InputValidator.isPin(input) || !pins.verify(s.msisdn(), input)) {
            s.fail("err.pin_wrong");                 // never log or store `input`
            return id();
        }
        if (s.quote == null || !Instant.now().isBefore(s.quote.rateExpiresAt())) {
            s.quote = null;
            s.fail("err.quote_expired");
            return ScreenId.CONFIRM;                 // shows the new rate
        }
        return send.send(s);
    }
}
