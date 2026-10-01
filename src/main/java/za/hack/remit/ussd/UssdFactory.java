package za.hack.remit.ussd;

import java.time.Duration;
import java.util.List;
import za.hack.remit.fees.LimitPolicy;
import za.hack.remit.fees.QuoteService;
import za.hack.remit.i18n.Messages;
import za.hack.remit.security.PinService;
import za.hack.remit.security.RateLimiter;
import za.hack.remit.transfer.TransferService;
import za.hack.remit.ussd.screens.*;

/** P1 calls this from Main. All dependencies are interfaces. */
public final class UssdFactory {
    private UssdFactory() {}

    public static UssdController create(Messages m, QuoteService quotes, TransferService transfers,
                                        PinService pins, RateLimiter limiter, LimitPolicy limits) {
        UssdSessionStore store = new UssdSessionStore(Duration.ofMinutes(5));
        List<Screen> screens = List.of(
                new LanguageScreen(m), new MainMenuScreen(m), new ResumeScreen(m),
                new AmountScreen(m, limits), new RecipientScreen(m),
                new ConfirmScreen(m, quotes), new PinScreen(m, quotes, transfers, pins),
                new TrackScreen(m, transfers), new ExitScreen(m));
        return new UssdController(store, screens, limiter, m);
    }
}
