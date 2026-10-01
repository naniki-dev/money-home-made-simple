package za.hack.remit.ussd.screens;

import za.hack.remit.i18n.Messages;
import za.hack.remit.ussd.*;

public class ResumeScreen extends BaseScreen {
    public ResumeScreen(Messages m) { super(m); }
    @Override public ScreenId id() { return ScreenId.RESUME; }
    @Override protected String body(UssdSession s) { return msg(s, "resume.prompt", s.get("amount")); }

    @Override public ScreenId handle(UssdSession s, String input) {
        var c = InputValidator.choice(input, 2);
        if (c.isEmpty()) { s.fail("err.invalid_choice"); return id(); }
        if (c.get() == 1) return ScreenId.valueOf(s.get("resumeTo"));
        s.resetJourney();
        return ScreenId.MAIN_MENU;
    }
}
