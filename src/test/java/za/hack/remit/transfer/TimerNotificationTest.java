package za.hack.remit.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import za.hack.remit.notify.ReceiverStatusNotifier;
import za.hack.remit.notify.SenderStatusNotifier;
import za.hack.remit.notify.SmsSimulator;
import za.hack.remit.notify.StatusMessages;

class TimerNotificationTest {

    private static long countTo(SmsSimulator sms, String masked) {
        return sms.recent().stream().filter(m -> m.toMasked().equals(masked)).count();
    }

    @Test
    void theTimerStopsAtReadyToCollectAndTheManualStepFinishesIt() {
        SmsSimulator sms = new SmsSimulator();
        TransferService svc = new TransferService();
        svc.addListener(new SenderStatusNotifier(sms, StatusMessages::forSender));
        svc.addListener(new ReceiverStatusNotifier(sms, StatusMessages::forReceiver));
        String ref = svc.create("s1", "+27821234567", "Mama", "+263771234567", "en",
                new BigDecimal("500.00"), new BigDecimal("25.00"), new BigDecimal("525.00"),
                new BigDecimal("17.80"), new BigDecimal("28.09")).getReference();

        assertTrue(svc.advanceIfIdle(ref, Duration.ZERO));    // IN_TRANSIT
        assertTrue(svc.advanceIfIdle(ref, Duration.ZERO));    // READY_TO_COLLECT
        assertFalse(svc.advanceIfIdle(ref, Duration.ZERO));   // the timer stops here
        assertEquals(TransferStatus.READY_TO_COLLECT, svc.findForAdmin(ref).orElseThrow().getStatus());
        assertEquals(3, countTo(sms, "+278*****567"));        // receipt, on its way, can collect
        assertEquals(1, countTo(sms, "+263******567"));       // ready to collect

        svc.advance(ref);                                     // the manual last step
        assertEquals(TransferStatus.COLLECTED, svc.findForAdmin(ref).orElseThrow().getStatus());
        assertEquals(4, countTo(sms, "+278*****567"));        // plus "Mama collected"
        assertEquals(2, countTo(sms, "+263******567"));       // plus "you collected"
    }
}
