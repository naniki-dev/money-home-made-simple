package za.hack.remit.ussd.screens;

import za.hack.remit.i18n.Messages;
import za.hack.remit.ussd.*;

/** Terminal screen (END). Message key and args are set by whichever screen routed here. */
public class ExitScreen extends BaseScreen {
    public ExitScreen(Messages m) { super(m); }
    @Override public ScreenId id() { return ScreenId.EXIT; }
    @Override protected boolean terminal() { return true; }

    @Override protected String body(UssdSession s) {
        String key = s.get("exitKey") == null ? "exit.bye" : s.get("exitKey");
        return msg(s, key, s.get("exitArg1"), s.get("exitArg2"));
    }

    @Override public ScreenId handle(UssdSession s, String input) { return ScreenId.MAIN_MENU; }
}
