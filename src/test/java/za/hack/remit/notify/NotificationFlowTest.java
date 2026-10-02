package za.hack.remit.notify;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.hack.remit.transfer.Recipient;
import za.hack.remit.transfer.Terms;
import za.hack.remit.transfer.Transfer;
import za.hack.remit.transfer.TransferService;
import za.hack.remit.transfer.TransferStatus;

class NotificationFlowTest {

    private static final String SENDER = "+27821234567";
    private static final String RECEIVER = "+263771234567";
    private static final String SENDER_MASKED = "+278*****567";
    private static final String RECEIVER_MASKED = "+263******567";
    private static final Terms TERMS = new Terms(new BigDecimal("500.00"), new BigDecimal("25.00"),
            new BigDecimal("525.00"), new BigDecimal("17.80"), new BigDecimal("28.09"));

    private SmsSimulator sms;
    private TransferService svc;

    @BeforeEach
    void setUp() {
        sms = new SmsSimulator();
        svc = new TransferService();
        svc.addListener(new SenderStatusNotifier(sms, StatusMessages::forSender));
        svc.addListener(new ReceiverStatusNotifier(sms, StatusMessages::forReceiver));
    }

    private Transfer make(String session, String name) {
        return svc.create(session, SENDER, new Recipient(name, RECEIVER), "en", TERMS);
    }

    /** Messages sent to one (masked) number, oldest first. */
    private List<String> textsTo(String masked) {
        List<String> out = new ArrayList<>();
        for (SmsSimulator.SmsMessage m : sms.recent()) {
            if (m.toMasked().equals(masked)) {
                out.add(m.text());
            }
        }
        Collections.reverse(out);
        return out;
    }

    @Test
    void theFullJourneySendsTheRightMessagesToTheRightPeople() {
        String ref = make("s1", "Mama").getReference();

        List<String> toSender = textsTo(SENDER_MASKED);
        assertEquals(1, toSender.size());
        String receipt = toSender.get(0);
        assertTrue(receipt.contains("Fee R25.00"));
        assertTrue(receipt.contains("Rate 17.80"));
        assertTrue(receipt.contains("R525.00"));
        assertTrue(receipt.contains("US$28.09"));
        assertTrue(receipt.contains(ref));
        assertTrue(textsTo(RECEIVER_MASKED).isEmpty());

        svc.advance(ref); // IN_TRANSIT
        assertTrue(textsTo(RECEIVER_MASKED).isEmpty());
        svc.advance(ref); // READY_TO_COLLECT
        svc.advance(ref); // COLLECTED

        assertEquals(List.of(
                        "Your money is ready to collect. US$28.09. Ref " + ref,
                        "You collected US$28.09. Ref " + ref + ". Not you? Contact support."),
                textsTo(RECEIVER_MASKED));

        toSender = textsTo(SENDER_MASKED);
        assertEquals(4, toSender.size());
        assertEquals("Your money to Mama is on its way. Ref " + ref, toSender.get(1));
        assertEquals("Mama can collect US$28.09 now. Ref " + ref, toSender.get(2));
        assertEquals("Mama collected US$28.09. Ref " + ref, toSender.get(3));
    }

    @Test
    void aRepeatedConfirmSendsOneReceiptOnly() {
        make("s1", "Mama");
        make("s1", "Mama");
        assertEquals(1, textsTo(SENDER_MASKED).size());
    }

    @Test
    void cancellingNotifiesTheSenderOnly() {
        String ref = make("s1", "Mama").getReference();
        svc.changeStatus(ref, TransferStatus.CANCELLED);
        List<String> toSender = textsTo(SENDER_MASKED);
        assertEquals("Transfer cancelled. Ref " + ref, toSender.get(toSender.size() - 1));
        assertTrue(textsTo(RECEIVER_MASKED).isEmpty());
    }

    @Test
    void aVeryLongRecipientNameStillFitsInOneSms() {
        make("s1", "M".repeat(200));
        List<String> toSender = textsTo(SENDER_MASKED);
        assertEquals(1, toSender.size());
        assertTrue(toSender.get(0).length() <= 160);
    }

    @Test
    void aFailingListenerDoesNotBlockOthersOrUndoTheMove() {
        TransferService local = new TransferService();
        local.addListener((t, from, to) -> {
            throw new IllegalStateException("boom");
        });
        local.addListener(new SenderStatusNotifier(sms, StatusMessages::forSender));

        Transfer t = local.create("s1", SENDER, new Recipient("Mama", RECEIVER), "en", TERMS);
        assertEquals(1, textsTo(SENDER_MASKED).size());
        local.advance(t.getReference());
        assertEquals(TransferStatus.IN_TRANSIT, t.getStatus());
        assertEquals(2, textsTo(SENDER_MASKED).size());
    }

    @Test
    void readyToCollectIsSentExactlyOnceEvenWithConcurrentAdvances() throws Exception {
        String ref = make("s1", "Mama").getReference();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    svc.advance(ref);
                } catch (IllegalStateException e) {
                    // expected for the calls that arrive after the end
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        pool.shutdown();

        long ready = textsTo(RECEIVER_MASKED).stream()
                .filter(s -> s.startsWith("Your money is ready to collect")).count();
        assertEquals(1, ready);
        assertEquals(2, textsTo(RECEIVER_MASKED).size());
    }
}