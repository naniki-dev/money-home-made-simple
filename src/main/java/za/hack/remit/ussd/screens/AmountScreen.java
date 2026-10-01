package za.hack.remit.ussd.screens;

import java.util.UUID;
import za.hack.remit.fees.DefaultQuoteCalculator;
import za.hack.remit.fees.Quote;
import za.hack.remit.fees.QuoteCalculator;
import za.hack.remit.fees.QuoteException;
import za.hack.remit.fx.FxService;
import za.hack.remit.i18n.Messages;
import za.hack.remit.security.Masker;
import za.hack.remit.ussd.*;

public class AmountScreen extends BaseScreen {
    private final FxService fx;
    private final QuoteCalculator quotes;

    public AmountScreen(Messages m, FxService fx, QuoteCalculator quotes) {
        super(m);
        this.fx = fx;
        this.quotes = quotes;
    }

    @Override public ScreenId id() { return ScreenId.AMOUNT; }

    @Override protected String body(UssdSession s) {
        String min = DefaultQuoteCalculator.MIN_AMOUNT_ZAR.toPlainString();
        String max = DefaultQuoteCalculator.MAX_AMOUNT_ZAR.toPlainString();
        return s.recipient != null
                ? msg(s, "amount.prompt.saved", min, max, Masker.msisdn(s.recipient))
                : msg(s, "amount.prompt", min, max);
    }

    @Override public ScreenId handle(UssdSession s, String input) {
        if ("0".equals(input)) {                     // "0 = change receiver"
            s.recipient = null;
            return ScreenId.RECIPIENT;
        }
        var amount = InputValidator.amount(input);
        if (amount.isEmpty()) { s.fail("err.invalid_amount"); return id(); }
        try {
            Quote q = quotes.quote(amount.get(), fx.lockRate("ZAR", "USD"));   // locks the rate
            s.quote = q;
            s.put("amount", q.amountZar().toPlainString());
            s.put("draftKey", UUID.randomUUID().toString());
            return ScreenId.CONFIRM;
        } catch (QuoteException e) {
            switch (e.messageKey()) {
                case QuoteException.MIN_AMOUNT, QuoteException.MAX_AMOUNT -> s.fail("err.out_of_range",
                        DefaultQuoteCalculator.MIN_AMOUNT_ZAR.toPlainString(),
                        DefaultQuoteCalculator.MAX_AMOUNT_ZAR.toPlainString());
                case QuoteException.RATE_EXPIRED -> s.fail("err.quote_expired");
                default -> s.fail("err.invalid_amount");
            }
            return id();
        }
    }
}
