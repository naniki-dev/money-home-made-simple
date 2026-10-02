package za.hack.remit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.hack.remit.fees.DefaultQuoteCalculator;
import za.hack.remit.fx.MockFxService;
import za.hack.remit.notify.SmsSimulator;
import za.hack.remit.recurring.RecurringService;
import za.hack.remit.transfer.Transfer;
import za.hack.remit.transfer.TransferService;
import za.hack.remit.transfer.TransferStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests RecurringService with the REAL calculator, FX service and TransferService.
 * Only SMS and transfers are wrapped, so the tests can read what was sent.
 * Self-contained: does not use Fixture.
 */
class RecurringServiceTest {

    private static final String PHONE = "+27821234567";
    private static final String OTHER_PHONE = "+27820000002";
    private static final String RECIPIENT = "0771234567";

    private static final LocalDate DAY_BEFORE = LocalDate.of(2026, 10, 14);
    private static final LocalDate SEND_DAY = LocalDate.of(2026, 10, 15);

    /** The real SmsSimulator, plus sent(): every message, oldest first. */
    static class RecordingSms extends SmsSimulator {
        List<SmsMessage> sent() {
            List<SmsMessage> oldestFirst = new ArrayList<>(recent());
            Collections.reverse(oldestFirst);
            return oldestFirst;
        }
    }

    /** The real TransferService, plus all(): every transfer created so far. */
    static class TrackingTransfers extends TransferService {
        private final List<Transfer> created = new CopyOnWriteArrayList<>();

        TrackingTransfers() {
            // from == null means "just created". If TransferListener has more than one method,
            // replace this lambda with an anonymous class.
            addListener((t, from, to) -> { if (from == null) created.add(t); });
        }

        List<Transfer> all() { return List.copyOf(created); }
    }

    private RecordingSms sms;
    private TrackingTransfers transfers;
    private RecurringService recurring;

    @BeforeEach
    void setUp() {
        MockFxService fx = new MockFxService();   // not started: the rate stays fixed
        sms = new RecordingSms();
        transfers = new TrackingTransfers();
        recurring = new RecurringService(transfers, new DefaultQuoteCalculator(), sms, fx);
        recurring.setup(PHONE, RECIPIENT, new BigDecimal("500"), 15);
    }

    // ---------- reminders ----------

    @Test
    void reminderTheDayBeforeHasASkipInstructionAndMovesNoMoney() {
        recurring.tick(DAY_BEFORE);

        assertEquals(1, sms.sent().size());
        String text = sms.sent().get(0).text();
        assertTrue(text.contains("*120*3#"), "must say how to skip");
        assertTrue(text.contains("R500"), "must show the amount");
        assertTrue(text.length() <= 160, "must fit in one SMS");
        assertEquals(0, transfers.all().size(), "must not send yet");
    }

    @Test
    void reminderIsSentOnlyOncePerMonth() {
        recurring.tick(DAY_BEFORE);
        recurring.tick(DAY_BEFORE);

        assertEquals(1, sms.sent().size());
    }

    @Test
    void planOnThe1stIsRemindedOnTheLastDayOfThePreviousMonth() {
        recurring.setup(OTHER_PHONE, RECIPIENT, new BigDecimal("100"), 1);

        recurring.tick(LocalDate.of(2026, 10, 31));

        assertEquals(1, sms.sent().size());
        assertTrue(sms.sent().get(0).text().contains("R100"));
        assertEquals(0, transfers.all().size());
    }

    // ---------- sending ----------

    @Test
    void sendsOnTheChosenDay() {
        recurring.tick(SEND_DAY);

        assertEquals(1, transfers.all().size());
    }

    @Test
    void doesNotSendOnOtherDays() {
        recurring.tick(LocalDate.of(2026, 10, 10));
        recurring.tick(LocalDate.of(2026, 10, 16));

        assertEquals(0, transfers.all().size());
    }

    @Test
    void schedulerRunningManyTimesNeverDoubleSends() {
        recurring.tick(SEND_DAY);
        recurring.tick(SEND_DAY);
        recurring.tick(SEND_DAY);

        assertEquals(1, transfers.all().size());
    }

    @Test
    void sentTransferCopiesTheQuoteExactly() {
        recurring.tick(SEND_DAY);

        Transfer t = transfers.all().get(0);
        assertEquals(PHONE, t.getSenderPhone());
        assertEquals(RECIPIENT, t.getRecipientPhone());
        assertEquals(new BigDecimal("500.00"), t.getAmountZar());
        assertEquals(new BigDecimal("25.00"), t.getFeeZar());
        assertEquals(new BigDecimal("525.00"), t.getTotalZar());
        assertEquals(new BigDecimal("500").divide(t.getRate(), 2, RoundingMode.HALF_UP),
                t.getReceiveAmountUsd());
        assertEquals(TransferStatus.SENT, t.getStatus());
    }

    @Test
    void transferUsesOneSendPerPlanPerMonthAsItsKey() {
        recurring.tick(SEND_DAY);

        assertEquals("recurring:" + PHONE + ":2026-10", transfers.all().get(0).getSessionId());
    }

    @Test
    void sendDayTextSaysTheMoneyWasSent() {
        recurring.tick(SEND_DAY);

        assertEquals(1, sms.sent().size());
        String text = sms.sent().get(0).text();
        assertTrue(text.contains("was sent"));
        assertTrue(text.length() <= 160);
    }

    @Test
    void reminderThenSendGivesTwoTextsAndOneTransfer() {
        recurring.tick(DAY_BEFORE);
        recurring.tick(SEND_DAY);

        assertEquals(2, sms.sent().size());
        assertEquals(1, transfers.all().size());
    }

    @Test
    void settingUpAgainReplacesThePlan() {
        recurring.setup(PHONE, RECIPIENT, new BigDecimal("200"), 15);

        recurring.tick(SEND_DAY);

        assertEquals(1, transfers.all().size());
        assertEquals(new BigDecimal("200.00"), transfers.all().get(0).getAmountZar());
    }

    // ---------- skip and cancel ----------

    @Test
    void skippingThisMonthSuppressesReminderAndSend() {
        assertTrue(recurring.skipNext(PHONE, LocalDate.of(2026, 10, 10)));

        recurring.tick(DAY_BEFORE);
        recurring.tick(SEND_DAY);

        assertEquals(0, sms.sent().size());
        assertEquals(0, transfers.all().size());
    }

    @Test
    void skippingOneMonthStillSendsNextMonth() {
        recurring.skipNext(PHONE, LocalDate.of(2026, 10, 10));

        recurring.tick(LocalDate.of(2026, 11, 15));

        assertEquals(1, transfers.all().size());
    }

    @Test
    void skipReturnsFalseWhenThereIsNoPlan() {
        assertFalse(recurring.skipNext("+27000000000", SEND_DAY));
    }

    @Test
    void cancelledPlanNeverSendsOrReminds() {
        recurring.cancel(PHONE);

        recurring.tick(DAY_BEFORE);
        recurring.tick(SEND_DAY);

        assertEquals(0, sms.sent().size());
        assertEquals(0, transfers.all().size());
        assertTrue(recurring.find(PHONE).isEmpty());
    }

    // ---------- dates ----------

    @Test
    void sendDayIsClampedTo28SoEveryMonthHasIt() {
        var plan = recurring.setup(OTHER_PHONE, RECIPIENT, new BigDecimal("100"), 31);

        assertEquals(28, plan.sendDay);
    }

    @Test
    void aDay31PlanStillSendsInFebruary() {
        recurring.setup(OTHER_PHONE, RECIPIENT, new BigDecimal("100"), 31);

        recurring.tick(LocalDate.of(2026, 2, 28));

        assertEquals(1, transfers.all().size());
        assertEquals(OTHER_PHONE, transfers.all().get(0).getSenderPhone());
    }

    @Test
    void nextOccurrenceIsThisMonthUntilTheMoneyIsSent() {
        assertEquals(YearMonth.of(2026, 10), recurring.nextOccurrenceMonth(PHONE, LocalDate.of(2026, 10, 10)));
        assertEquals(YearMonth.of(2026, 10), recurring.nextOccurrenceMonth(PHONE, SEND_DAY));
    }

    @Test
    void nextOccurrenceMovesToNextMonthAfterTheSendDayOrAfterSending() {
        assertEquals(YearMonth.of(2026, 11), recurring.nextOccurrenceMonth(PHONE, LocalDate.of(2026, 10, 16)));

        recurring.tick(SEND_DAY);

        assertEquals(YearMonth.of(2026, 11), recurring.nextOccurrenceMonth(PHONE, SEND_DAY));
    }

    // ---------- one bad plan must not hurt the others ----------

    @Test
    void oneBadPlanDoesNotStopTheOthers() {
        recurring.setup("+27820000009", RECIPIENT, new BigDecimal("10"), 15);   // below the R50 minimum

        recurring.tick(SEND_DAY);

        assertEquals(1, transfers.all().size());
        assertEquals(PHONE, transfers.all().get(0).getSenderPhone());
    }
}