package za.hack.remit.ussd.screens;

import za.hack.remit.i18n.Messages;
import za.hack.remit.ussd.Screen;
import za.hack.remit.ussd.UssdResponse;
import za.hack.remit.ussd.UssdSession;

abstract class BaseScreen implements Screen {
    protected final Messages messages;

    BaseScreen(Messages messages) { this.messages = messages; }

    protected abstract String body(UssdSession s);
    protected boolean terminal() { return false; }

    protected String msg(UssdSession s, String key, Object... args) {
        return messages.get(s.lang(), key, args);
    }

    @Override
    public final UssdResponse render(UssdSession s) {
        String text = body(s);
        if (s.errorKey() != null) {          // one short error line above the prompt
            text = messages.get(s.lang(), s.errorKey(), s.errorArgs()) + "\n" + text;
        }
        return terminal() ? UssdResponse.end(text) : UssdResponse.con(text);
    }
}
