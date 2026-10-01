package za.hack.remit.ussd;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import za.hack.remit.i18n.Messages;
import za.hack.remit.security.RateLimiter;

/**
 * Entry point for POST /ussd. Follows the team contract: text is the whole "1*2*3" string,
 * "" on the first dial, and the reply starts with CON or END.
 * Only the LAST item of text is used; the journey state is kept per phone number on the server.
 */
public class UssdController {
    private static final int MAX_ERRORS = 3;
    private static final long DRAFT_MAX_AGE_MS = 5 * 60 * 1000L;
    private static final Set<ScreenId> MID_JOURNEY = Set.of(ScreenId.CONFIRM, ScreenId.PIN);

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

    /** What the HTTP route returns: "CON ..." or "END ...". */
    public String handle(String sessionId, String phone, String text) {
        return handleResponse(sessionId, phone, text).toGatewayString();
    }

    public UssdResponse handleResponse(String sessionId, String phone, String text) {
        if (!rateLimiter.allow(phone)) {
            return UssdResponse.end(messages.get("en", "err.rate_limited"));
        }
        boolean freshDial = text == null || text.isBlank();
        String input = freshDial ? "" : text.substring(text.lastIndexOf('*') + 1).trim();

        UssdSession s = store.find(phone).orElseGet(() -> new UssdSession(phone, sessionId));
        s.setSessionId(sessionId);

        if (freshDial) {                             // *120# dialled
            s.clearError();
            s.resetErrorCount();
            ScreenId cur = s.current();
            if (MID_JOURNEY.contains(cur) && !s.isFresh(DRAFT_MAX_AGE_MS)) {
                s.resetJourney();                    // draft too old: start clean
            } else if (MID_JOURNEY.contains(cur)) {  // dropped mid-transfer -> offer to resume
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
        if (r.end()) s.resetJourney();               // render first, then wipe the draft
        store.save(s);
        return r;
    }
}
