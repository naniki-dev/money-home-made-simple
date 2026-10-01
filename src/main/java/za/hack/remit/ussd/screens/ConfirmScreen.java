package za.hack.remit.ussd.screens;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import za.hack.remit.fees.Quote;
import za.hack.remit.fees.QuoteService;
import za.hack.remit.i18n.Messages;
import za.hack.remit.security.Masker;
import za.hack.remit.ussd.*;

/** The trust screen: amount, fee, rate, what Mum receives, how long the rate is held. */
public class ConfirmScreen extends BaseScreen {
    private final QuoteService quotes;
    public ConfirmScreen(Messages m, QuoteService quotes) { super(m); this.quotes = quotes; }
    @Override public ScreenId id() { return ScreenId.CONFIRM; }

    @Override protected String body(UssdSession s) {
        Quote q = currentQuote(s);
        long mins = Math.max(1, (Duration.between(Instant.now(), q.expiresAt()).toSeconds() + 59) / 60);
        return msg(s, "confirm.summary",
                q.sendZar().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                q.feeZar().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                q.rate().stripTrailingZeros().toPlainString(),
                q.receiveUsd().setScale(2, RoundingMode.HALF_DOWN).toPlainString(),
                Masker.msisdn(s.get("recipient")),
                mins);
    }

    /** Re-quotes if the locked quote is missing or expired; the user always confirms what they see. */
    private Quote currentQuote(UssdSession s) {
        return quotes.find(s.get("quoteId"))
                .filter(q -> !q.isExpired(Instant.now()))
                .orElseGet(() -> {
                    Quote fresh = quotes.quote(new BigDecimal(s.get("amount")));
                    s.put("quoteId", fresh.quoteId());
                    return fresh;
                });
    }

    @Override public ScreenId handle(UssdSession s, String input) {
        var c = InputValidator.choice(input, 2);
        if (c.isEmpty()) { s.fail("err.invalid_choice"); return id(); }
        if (c.get() == 2) { s.resetJourney(); return ScreenId.MAIN_MENU; }
        return ScreenId.PIN;
    }
}
