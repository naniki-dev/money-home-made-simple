package za.hack.remit.ussd.screens;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import za.hack.remit.fees.Quote;
import za.hack.remit.fees.QuoteCalculator;
import za.hack.remit.fx.FxService;
import za.hack.remit.i18n.Messages;
import za.hack.remit.security.Masker;
import za.hack.remit.ussd.*;

/** The trust screen: amount, fee, total, rate, what Mama gets, how long the rate is held. */
public class ConfirmScreen extends BaseScreen {
    private final FxService fx;
    private final QuoteCalculator quotes;
    private final SendAction send;
    private final boolean usePin;

    public ConfirmScreen(Messages m, FxService fx, QuoteCalculator quotes, SendAction send, boolean usePin) {
        super(m);
        this.fx = fx;
        this.quotes = quotes;
        this.send = send;
        this.usePin = usePin;
    }

    @Override public ScreenId id() { return ScreenId.CONFIRM; }

    @Override protected String body(UssdSession s) {
        Quote q = currentQuote(s);
        long mins = Math.max(1, (Duration.between(Instant.now(), q.rateExpiresAt()).toSeconds() + 59) / 60);
        return msg(s, "confirm.summary",
                q.amountZar().toPlainString(), q.feeZar().toPlainString(), q.totalZar().toPlainString(),
                q.rate().toPlainString(), q.receiverGetsUsd().toPlainString(),
                Masker.msisdn(s.recipient), mins);
    }

    /** If the locked rate ran out, lock a fresh one; the user always confirms the numbers on screen. */
    private Quote currentQuote(UssdSession s) {
        if (s.quote == null || !Instant.now().isBefore(s.quote.rateExpiresAt())) {
            s.quote = quotes.quote(new BigDecimal(s.get("amount")), fx.lockRate("ZAR", "USD"));
        }
        return s.quote;
    }

    @Override public ScreenId handle(UssdSession s, String input) {
        var c = InputValidator.choice(input, 2);
        if (c.isEmpty()) { s.fail("err.invalid_choice"); return id(); }
        if (c.get() == 2) { s.resetJourney(); return ScreenId.MAIN_MENU; }
        if (s.quote == null || !Instant.now().isBefore(s.quote.rateExpiresAt())) {
            s.quote = null;                          // render() will lock the new rate and show it
            s.fail("err.quote_expired");
            return id();
        }
        return usePin ? ScreenId.PIN : send.send(s);
    }
}
