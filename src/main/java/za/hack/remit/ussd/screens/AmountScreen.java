package za.hack.remit.ussd.screens;

import java.math.BigDecimal;
import za.hack.remit.fees.LimitPolicy;
import za.hack.remit.i18n.Messages;
import za.hack.remit.ussd.*;

public class AmountScreen extends BaseScreen {
    private final LimitPolicy limits;
    public AmountScreen(Messages m, LimitPolicy limits) { super(m); this.limits = limits; }
    @Override public ScreenId id() { return ScreenId.AMOUNT; }

    @Override protected String body(UssdSession s) {
        return msg(s, "amount.prompt", limits.min().toPlainString(), limits.max().toPlainString());
    }

    @Override public ScreenId handle(UssdSession s, String input) {
        var amount = InputValidator.amount(input);
        if (amount.isEmpty()) { s.fail("err.invalid_amount"); return id(); }
        BigDecimal a = amount.get();
        if (a.compareTo(limits.min()) < 0 || a.compareTo(limits.max()) > 0) {
            s.fail("err.out_of_range", limits.min().toPlainString(), limits.max().toPlainString());
            return id();
        }
        s.put("amount", a.toPlainString());
        return ScreenId.RECIPIENT;
    }
}
