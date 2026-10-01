package za.hack.remit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class RecurringServiceTest {
    private Fixture f;
    private static final String P = Fixture.PHONE;
    private static final LocalDate DAY_BEFORE = LocalDate.of(2026, 10, 14);
    private static final LocalDate SEND_DAY   = LocalDate.of(2026, 10, 15);

    @BeforeEach
    void setUp() {
        f = new Fixture();
        f.recurring.setup(P, "0771234567", new BigDecimal("500"), 15);
    }

    @Test
    void remindsTheDayBeforeWithASkipInstruction() {
        f.recurring.tick(DAY_BEFORE);
        assertEquals(1, f.sms.sent().size());
        assertTrue(f.sms.sent().get(0).text().contains("*120*3#"));
        assertTrue(f.sms.sent().get(0).text().length() <= 160);
        assertEquals(0, f.transfers.all().size(), "must not send yet");
    }

    @Test
    void reminderIsSentOnlyOncePerMonth() {
        f.recurring.tick(DAY_BEFORE);
        f.recurring.tick(DAY_BEFORE);
        assertEquals(1, f.sms.sent().size());
    }

    @Test
    void sendsOnTheChosenDayIfNotSkipped() {
        f.recurring.tick(SEND_DAY);
        assertEquals(1, f.transfers.all().size());
    }

    @Test
    void schedulerRunningTwiceNeverDoubleSends() {
        f.recurring.tick(SEND_DAY);
        f.recurring.tick(SEND_DAY);
        f.recurring.tick(SEND_DAY);
        assertEquals(1, f.transfers.all().size());
    }

    @Test
    void skippingThisMonthSuppressesReminderAndSend() {
        assertTrue(f.recurring.skipNext(P, LocalDate.of(2026, 10, 10)));
        f.recurring.tick(DAY_BEFORE);
        f.recurring.tick(SEND_DAY);
        assertEquals(0, f.transfers.all().size());
    }

    @Test
    void skippingOneMonthStillSendsNextMonth() {
        f.recurring.skipNext(P, LocalDate.of(2026, 10, 10));
        f.recurring.tick(LocalDate.of(2026, 11, 15));
        assertEquals(1, f.transfers.all().size());
    }

    @Test
    void cancelledPlanNeverSends() {
        f.recurring.cancel(P);
        f.recurring.tick(SEND_DAY);
        assertEquals(0, f.transfers.all().size());
    }

    @Test
    void sendDayIsClampedTo28SoEveryMonthHasIt() {
        var plan = f.recurring.setup("+27820000002", "0771234567", new BigDecimal("100"), 31);
        assertEquals(28, plan.sendDay);
    }
}