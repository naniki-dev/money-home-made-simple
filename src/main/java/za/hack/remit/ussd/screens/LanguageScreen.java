package za.hack.remit.ussd.screens;

import za.hack.remit.i18n.Messages;
import za.hack.remit.ussd.*;

public class LanguageScreen extends BaseScreen {
    public LanguageScreen(Messages m) { super(m); }
    @Override public ScreenId id() { return ScreenId.LANGUAGE; }

    @Override protected String body(UssdSession s) {
        return "Welcome / Mhoroi\n1 English\n2 chiShona";   // intentionally hard-coded: language not chosen yet
    }

    @Override public ScreenId handle(UssdSession s, String input) {
        var c = InputValidator.choice(input, 2);
        if (c.isEmpty()) { s.fail("err.invalid_choice"); return id(); }
        s.setLanguage(c.get() == 1 ? "en" : "sn");
        return ScreenId.MAIN_MENU;
    }
}
