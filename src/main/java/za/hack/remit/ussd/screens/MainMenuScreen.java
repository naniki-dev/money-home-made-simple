package za.hack.remit.ussd.screens;

import za.hack.remit.i18n.Messages;
import za.hack.remit.ussd.*;

public class MainMenuScreen extends BaseScreen {
    public MainMenuScreen(Messages m) { super(m); }
    @Override public ScreenId id() { return ScreenId.MAIN_MENU; }
    @Override protected String body(UssdSession s) { return msg(s, "menu.main"); }

    @Override public ScreenId handle(UssdSession s, String input) {
        if ("0".equals(input)) return ScreenId.EXIT;
        var c = InputValidator.choice(input, 3);
        if (c.isEmpty()) { s.fail("err.invalid_choice"); return id(); }
        return switch (c.get()) {
            case 1 -> s.recipient == null ? ScreenId.RECIPIENT : ScreenId.AMOUNT;   // saved receiver: skip a screen
            case 2 -> ScreenId.TRACK;
            default -> ScreenId.LANGUAGE;
        };
    }
}
