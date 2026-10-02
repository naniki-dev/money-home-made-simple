package za.hack.remit.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TransferServiceTest {

    private static final String SENDER = "+27821234567";
    private static final String RECEIVER = "+263771234567";
    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    /// @param svc
    /// @param session
    /// @return
    private static Transfer make(TransferService svc, String session) {
        return svc.create(session, SENDER, "Mama", RECEIVER, "en",
                bd("500.00"), bd("25.00"), bd("525.00"), bd("17.80"), bd("28.09"));
    }

    @Test
    void createIsIdempotentForTheSameSession() {
        TransferService svc = new TransferService();
        Transfer a = make(svc, "s1");
        Transfer b = make(svc, "s1");
        assertSame(a, b);
        assertEquals(1, svc.listActive().size());
    }

    @Test
    void concurrentCreatesFromOneSessionMakeOneTransfer() throws Exception {
        TransferService svc = new TransferService();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return make(svc, "same-session").getReference();
            }));
        }
        start.countDown();
        Set<String> references = new HashSet<>();
        for (Future<String> f : results) {
            references.add(f.get(5, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertEquals(1, references.size());
        assertEquals(1, svc.listActive().size());
    }

    @Test
    void referencesHaveTheRightShapeAndAreUnique() {
        TransferService svc = new TransferService();
        Pattern shape = Pattern.compile("^RM-[A-HJKMNP-Z2-9]{6}$");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String ref = make(svc, "s" + i).getReference();
            assertTrue(shape.matcher(ref).matches(), ref);
            assertTrue(seen.add(ref), "duplicate " + ref);
        }
    }

    @Test
    void findOnlyReturnsTheTransferToItsSender() {
        TransferService svc = new TransferService();
        Transfer t = make(svc, "s1");
        assertTrue(svc.find(t.getReference(), SENDER).isPresent());
        assertTrue(svc.find("  " + t.getReference().toLowerCase() + " ", SENDER).isPresent());
        assertTrue(svc.find(t.getReference(), "+27829999999").isEmpty());
        assertTrue(svc.find(t.getReference(), null).isEmpty());
        assertTrue(svc.find("RM-AAAAAA", SENDER).isEmpty());
        assertTrue(svc.find(null, SENDER).isEmpty());
    }

    @Test
    void advanceWalksEveryStageThenRefuses() {
        TransferService svc = new TransferService();
        Transfer t = make(svc, "s1");
        String ref = t.getReference();
        assertEquals(TransferStatus.SENT, t.getStatus());
        assertEquals(TransferStatus.IN_TRANSIT, svc.advance(ref).getStatus());
        assertEquals(TransferStatus.READY_TO_COLLECT, svc.advance(ref).getStatus());
        assertEquals(TransferStatus.COLLECTED, svc.advance(ref).getStatus());
        assertThrows(IllegalStateException.class, () -> svc.advance(ref));
        assertThrows(NoSuchElementException.class, () -> svc.advance("RM-AAAAAA"));
    }

    @Test
    void changeStatusEnforcesTheRules() {
        TransferService svc = new TransferService();
        Transfer t = make(svc, "s1");
        String ref = t.getReference();
        assertThrows(IllegalStateException.class, () -> svc.changeStatus(ref, TransferStatus.COLLECTED));
        assertEquals(TransferStatus.SENT, t.getStatus());
        svc.changeStatus(ref, TransferStatus.CANCELLED);
        assertThrows(IllegalStateException.class, () -> svc.advance(ref));
        assertThrows(IllegalStateException.class, () -> svc.changeStatus(ref, TransferStatus.SENT));
    }

    @Test
    void advanceIfIdleWaitsForTheIdleTime() {
        TransferService svc = new TransferService();
        Transfer t = make(svc, "s1");
        String ref = t.getReference();
        assertFalse(svc.advanceIfIdle(ref, Duration.ofHours(1)));
        assertEquals(TransferStatus.SENT, t.getStatus());
        assertTrue(svc.advanceIfIdle(ref, Duration.ZERO));
        assertEquals(TransferStatus.IN_TRANSIT, t.getStatus());
        svc.changeStatus(ref, TransferStatus.FAILED);
        assertFalse(svc.advanceIfIdle(ref, Duration.ZERO));
        assertFalse(svc.advanceIfIdle("RM-AAAAAA", Duration.ZERO));
    }

    @Test
    void listActiveLeavesOutFinishedTransfers() {
        TransferService svc = new TransferService();
        Transfer done = make(svc, "s1");
        make(svc, "s2");
        svc.changeStatus(done.getReference(), TransferStatus.CANCELLED);
        assertEquals(1, svc.listActive().size());
    }

    @Test
    void concurrentAdvancesNeverSkipAStage() throws Exception {
        TransferService svc = new TransferService();
        String ref = make(svc, "s1").getReference();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger moved = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    svc.advance(ref);
                    moved.incrementAndGet();
                } catch (IllegalStateException e) {
                    refused.incrementAndGet();
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
        assertEquals(3, moved.get());
        assertEquals(5, refused.get());
        assertEquals(TransferStatus.COLLECTED, svc.findForAdmin(ref).orElseThrow().getStatus());
    }

    @Test
    void createRejectsBadInput() {
        TransferService svc = new TransferService();
        BigDecimal a = bd("500.00"), f = bd("25.00"), t = bd("525.00"), r = bd("17.80"), u = bd("28.09");

        assertThrows(IllegalArgumentException.class, () -> svc.create(" ", SENDER, "Mama", RECEIVER, "en", a, f, t, r, u));
        assertThrows(IllegalArgumentException.class, () -> svc.create("s1", "", "Mama", RECEIVER, "en", a, f, t, r, u));
        assertThrows(IllegalArgumentException.class, () -> svc.create("s1", SENDER, " ", RECEIVER, "en", a, f, t, r, u));
        assertThrows(IllegalArgumentException.class, () -> svc.create("s1", SENDER, "Mama", "", "en", a, f, t, r, u));
        assertThrows(IllegalArgumentException.class, () -> svc.create("s1", SENDER, "Mama", RECEIVER, "en", bd("0"), f, t, r, u));
        assertThrows(IllegalArgumentException.class, () -> svc.create("s1", SENDER, "Mama", RECEIVER, "en", a, bd("-1"), t, r, u));
        assertThrows(IllegalArgumentException.class, () -> svc.create("s1", SENDER, "Mama", RECEIVER, "en", a, f, bd("0"), r, u));
        assertThrows(IllegalArgumentException.class, () -> svc.create("s1", SENDER, "Mama", RECEIVER, "en", a, f, t, bd("0"), u));
        assertThrows(IllegalArgumentException.class, () -> svc.create("s1", SENDER, "Mama", RECEIVER, "en", a, f, t, r, null));
        assertDoesNotThrow(() -> svc.create("s2", SENDER, "Mama", RECEIVER, "en", a, bd("0"), t, r, u));
    }


    @Test
    void objectsNeverPrintPhoneNumbers() {
        TransferService svc = new TransferService();
        Transfer t = make(svc, "s1");
        assertFalse(t.toString().contains("771234567"));
        assertFalse(t.toString().contains("771234567"));
    }
}