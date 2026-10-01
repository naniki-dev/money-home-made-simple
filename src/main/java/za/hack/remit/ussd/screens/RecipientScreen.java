package za.hack.remit.ussd.screens;

import za.hack.remit.i18n.Messages;
import za.hack.remit.ussd.*;

public class RecipientScreen extends BaseScreen {
    public RecipientScreen(Messages m) { super(m); }
    @Override public ScreenId id() { return ScreenId.RECIPIENT; }
    @Override protected String body(UssdSession s) { return msg(s, "recipient.prompt"); }

    @Override public ScreenId handle(UssdSession s, String input) {
        var n = InputValidator.zimbabweMsisdn(input);
        if (n.isEmpty()) { s.fail("err.invalid_number"); return id(); }
        s.recipient = n.get();                       // remembered for the next send
        return ScreenId.AMOUNT;
    }
}
