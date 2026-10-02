package za.hack.remit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import za.hack.remit.fees.DefaultQuoteCalculator;
import za.hack.remit.fx.MockFxService;
import za.hack.remit.i18n.Messages;
import za.hack.remit.notify.SmsSimulator;
import za.hack.remit.recurring.RecurringService;
import za.hack.remit.transfer.Transfer;
import za.hack.remit.transfer.TransferService;
import za.hack.remit.ussd.UssdController;
import za.hack.remit.ussd.UssdFactory;

/** Builds the real services for tests. A new Fixture per test means a clean slate. */
public class Fixture {

    public static final String PHONE = "+27821234567";

    /** The real SmsSimulator, plus sent(): every message, oldest first. */
    public static class RecordingSms extends SmsSimulator {
        public List<SmsMessage> sent() {
            List<SmsMessage> oldestFirst = new ArrayList<>(recent());   // recent() is newest first
            Collections.reverse(oldestFirst);
            return oldestFirst;
        }
    }

    /** The real TransferService, plus all(): every transfer created so far. */
    public static class TrackingTransfers extends TransferService {
        private final List<Transfer> created = new CopyOnWriteArrayList<>();

        public TrackingTransfers() {
            // from == null means "just created". If TransferListener has more than one method,
            // replace this lambda with an anonymous class.
            addListener((t, from, to) -> { if (from == null) created.add(t); });
        }

        public List<Transfer> all() {
            return List.copyOf(created);
        }
    }

    // MockFxService is NOT started here, so the rate stays fixed and tests are repeatable.
    public final MockFxService fx = new MockFxService();
    public final DefaultQuoteCalculator calculator = new DefaultQuoteCalculator();
    public final TrackingTransfers transfers = new TrackingTransfers();
    public final RecordingSms sms = new RecordingSms();
    public final RecurringService recurring = new RecurringService(transfers, calculator, sms, fx);

    // Same wiring as ConsoleDemo (null = no PIN step).
    public final UssdController ussd =
            UssdFactory.create(new Messages(), fx, calculator, transfers, null, key -> true);

    private int sessionCounter = 0;
    private String session = "sess-0";

    /** Dial *120# in a brand-new session. Returns the screen text ("CON ..." or "END ..."). */
    public String freshDial() {
        session = "sess-" + (++sessionCounter);
        return ussd.handle(session, PHONE, "");
    }

    /** Continue the current session. dial("1", "500") means the user typed 1 then 500. */
    public String dial(String... inputs) {
        return ussd.handle(session, PHONE, String.join("*", inputs));
    }
}