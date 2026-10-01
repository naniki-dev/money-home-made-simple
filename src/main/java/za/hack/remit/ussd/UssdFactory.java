package za.hack.remit.ussd;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import za.hack.remit.fees.QuoteCalculator;
import za.hack.remit.fx.FxService;
import za.hack.remit.i18n.Messages;
import za.hack.remit.security.PinService;
import za.hack.remit.security.RateLimiter;
import za.hack.remit.transfer.TransferService;
import za.hack.remit.ussd.screens.*;

/** P1 calls this from Main with the REAL services. pins may be null = no PIN step. */
public final class UssdFactory {
    private UssdFactory() {}

    public static UssdController create(Messages m, FxService fx, QuoteCalculator quotes,
                                        TransferService transfers, PinService pinsOrNull,
                                        RateLimiter limiter) {
        // Sessions live long so language and receiver are remembered; old drafts are dropped by the controller.
        UssdSessionStore store = new UssdSessionStore(Duration.ofDays(30));
        SendAction send = new SendAction(m, transfers);
        List<Screen> screens = new ArrayList<>(List.of(
                new LanguageScreen(m), new MainMenuScreen(m), new ResumeScreen(m),
                new RecipientScreen(m), new AmountScreen(m, fx, quotes),
                new ConfirmScreen(m, fx, quotes, send, pinsOrNull != null),
                new TrackScreen(m, transfers), new ExitScreen(m)));
        if (pinsOrNull != null) screens.add(new PinScreen(m, pinsOrNull, send));
        return new UssdController(store, screens, limiter, m);
    }
}
