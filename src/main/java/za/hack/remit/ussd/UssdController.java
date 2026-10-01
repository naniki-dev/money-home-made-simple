package za.hack.remit.ussd;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import za.hack.remit.i18n.Messages;
import za.hack.remit.security.RateLimiter;

/**
 * Single entry point for the gateway / simulator.
 * Contract: text is "" on the first dial, otherwise ONLY the latest input
 * (state is kept server-side, not in a 1*500*1 string).
 */
public class UssdController {
    private static final int MAX_ERRORS = 3;
    private static final Set<ScreenId> MID_JOURNEY = Set.of(ScreenId.RECIPIENT, ScreenId.CONFIRM, ScreenId.PIN);

    private final UssdSessionStore store;
    private final Map<ScreenId, Screen> screens = new EnumMap<>(ScreenId.class);
    private final RateLimiter rateLimiter;
    private final Messages messages;

    public UssdController(UssdSessionStore store, List<Screen> screenList, RateLimiter rateLimiter, Messages messages) {
        this.store = store;
        this.rateLimiter = rateLimiter;
        this.messages = messages;
        screenList.forEach(sc -> screens.put(sc.id(), sc));
    }

    public UssdResponse handle(String msisdn, String sessionId, String text) {
        if (!rateLimiter.allow(msisdn)) {
            return UssdResponse.end(messages.get("en", "err.rate_limited"));
        }
        String input = text == null ? "" : text.trim();
        UssdSession s = store.find(msisdn).orElseGet(() -> new UssdSession(msisdn, sessionId));
        s.setSessionId(sessionId);

        if (input.isEmpty()) {                       // fresh dial of *120#
            s.clearError();
            s.resetErrorCount();
            ScreenId cur = s.current();
            if (MID_JOURNEY.contains(cur)) {         // dropped mid-transfer -> offer to resume
                s.put("resumeTo", (cur == ScreenId.PIN ? ScreenId.CONFIRM : cur).name());
                s.setCurrent(ScreenId.RESUME);
            } else if (cur != ScreenId.LANGUAGE && cur != ScreenId.MAIN_MENU && cur != ScreenId.RESUME) {
                s.resetJourney();
            }
            return finish(s);
        }

        ScreenId before = s.current();
        s.clearError();
        ScreenId next = screens.get(before).handle(s, input);
        s.setCurrent(next);

        if (s.errorKey() != null && next == before) {
            if (s.bumpErrors() >= MAX_ERRORS) {
                s.resetJourney();
                store.save(s);
                return UssdResponse.end(messages.get(s.lang(), "err.too_many"));
            }
        } else {
            s.resetErrorCount();
        }
        return finish(s);
    }

    private UssdResponse finish(UssdSession s) {
        UssdResponse r = screens.get(s.current()).render(s);
        if (r.end()) s.resetJourney();               // render first, then wipe
        store.save(s);
        return r;
    }
}
