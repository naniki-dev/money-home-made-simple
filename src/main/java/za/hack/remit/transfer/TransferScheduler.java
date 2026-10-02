package za.hack.remit.transfer;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

//TODO:
//TransferScheduler scheduler = new TransferScheduler(transfers, Duration.ofSeconds(15));
//scheduler.start();
//Runtime.getRuntime().addShutdownHook(new Thread(scheduler::stop));
public class TransferScheduler {

    private static final Logger LOG = Logger.getLogger(TransferScheduler.class.getName());

    private final TransferService service;
    private final Duration stepDelay;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread th = new Thread(r, "transfer-scheduler");
                th.setDaemon(true);
                return th;
            });

    public TransferScheduler(TransferService service, Duration stepDelay) {
        if (service == null || stepDelay == null || stepDelay.isNegative()) {
            throw new IllegalArgumentException("service and a non-negative stepDelay are required");
        }
        this.service = service;
        this.stepDelay = stepDelay;
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::tick, 2, 2, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void tick() {
        try {
            for (Transfer t : service.listActive()) {
                try {
                    service.advanceIfIdle(t.getReference(), stepDelay);
                } catch (RuntimeException e) {
                    LOG.warning("Could not advance a transfer");
                }
            }
        } catch (RuntimeException e) {
            LOG.warning("Scheduler tick failed");
        }
    }
}

