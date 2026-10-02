package za.hack.remit;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import za.hack.remit.admin.AdminController;
import za.hack.remit.admin.DemoController;
import za.hack.remit.fees.DefaultQuoteCalculator;
import za.hack.remit.fx.MockFxService;
import za.hack.remit.i18n.Messages;
import za.hack.remit.notify.ReceiverStatusNotifier;
import za.hack.remit.notify.SenderStatusNotifier;
import za.hack.remit.notify.SmsSimulator;
import za.hack.remit.notify.StatusMessages;
import za.hack.remit.recurring.DemoDate;
import za.hack.remit.recurring.RecurringService;
import za.hack.remit.security.Masker;
import za.hack.remit.security.PinService;
import za.hack.remit.security.SimplePinService;
import za.hack.remit.security.SlidingWindowRateLimiter;
import za.hack.remit.transfer.TransferScheduler;
import za.hack.remit.transfer.TransferService;
import za.hack.remit.ussd.UssdController;
import za.hack.remit.ussd.UssdFactory;

/**
 * The real application. Environment variables (all optional):
 *   PORT            default 7070
 *   ADMIN_KEY       key for the admin panel; a random one is printed at startup if not set
 *   GATEWAY_SECRET  if set, POST /ussd must carry the header X-Gateway-Secret with this value
 *   USE_PIN         "false" turns the PIN step off (faster demo); default is ON
 *   STEP_SECONDS    seconds between automatic status steps, default 20
 */
public class Main {
    public static void main(String[] args) {
        int port = intEnv("PORT", 7070);
        int stepSeconds = intEnv("STEP_SECONDS", 20);
        boolean usePin = !"false".equalsIgnoreCase(System.getenv("USE_PIN"));
        String gatewaySecret = System.getenv("GATEWAY_SECRET");

        String adminKey = System.getenv("ADMIN_KEY");
        boolean generatedKey = adminKey == null || adminKey.isBlank();
        if (generatedKey) adminKey = randomKey();

        // ---------- build the object graph (the same order the tests use) ----------
        Messages messages = new Messages();
        MockFxService fx = new MockFxService();
        fx.start();                                            // moving rate
        DefaultQuoteCalculator quotes = new DefaultQuoteCalculator();
        SmsSimulator sms = new SmsSimulator();
        TransferService transfers = new TransferService();
        DemoDate demoDate = new DemoDate();
        RecurringService recurring = new RecurringService(transfers, quotes, sms, fx);
        PinService pins = usePin ? new SimplePinService() : null;
        UssdController ussd = UssdFactory.create(messages, fx, quotes, transfers, recurring, demoDate, pins,
                new SlidingWindowRateLimiter(60, Duration.ofMinutes(1)));

        // ---------- SMS on every status change ----------
        transfers.addListener(new SenderStatusNotifier(sms, StatusMessages::forSender));
        transfers.addListener(new ReceiverStatusNotifier(sms, StatusMessages::forReceiver));

        // ---------- background jobs ----------
        TransferScheduler scheduler = new TransferScheduler(transfers, Duration.ofSeconds(stepSeconds));
        scheduler.start();
        ScheduledExecutorService monthly = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "recurring-ticker");
            t.setDaemon(true);
            return t;
        });
        monthly.scheduleWithFixedDelay(() -> {
            try { recurring.tick(demoDate.today()); } catch (RuntimeException e) { System.err.println("Recurring tick failed"); }
        }, 5, 5, TimeUnit.SECONDS);

        // ---------- web server ----------
        Javalin app = Javalin.create(config -> config.staticFiles.add(files -> {
            files.hostedPath = "/";
            files.directory = "/simulator";
            files.location = Location.CLASSPATH;
        }));

        // The gateway (e.g. Africa's Talking) posts sessionId, phoneNumber and text as form fields.
        app.post("/ussd", ctx -> {
            if (gatewaySecret != null && !gatewaySecret.isBlank()) {
                String got = ctx.header("X-Gateway-Secret");
                if (got == null || !MessageDigest.isEqual(gatewaySecret.getBytes(StandardCharsets.UTF_8),
                        got.getBytes(StandardCharsets.UTF_8))) {
                    ctx.status(401).result("Unauthorised");
                    return;
                }
            }
            String phone = ctx.formParam("phoneNumber");
            if (phone == null || phone.isBlank()) {
                ctx.status(400).result("phoneNumber is required");
                return;
            }
            String reply;
            try {
                reply = ussd.handle(ctx.formParam("sessionId"), phone.trim(), ctx.formParam("text"));
            } catch (RuntimeException e) {
                System.err.println("USSD error for " + Masker.msisdn(phone) + ": " + e.getClass().getSimpleName());
                reply = "END Sorry, something went wrong. Please try again.";
            }
            ctx.contentType("text/plain; charset=utf-8").result(reply);
        });
        app.get("/health", ctx -> ctx.result("ok"));

        new AdminController(transfers, sms, adminKey).register(app);              // /admin/advance, /api/sms
        new DemoController(transfers, fx, recurring, demoDate, adminKey).register(app);   // /api/rate, /api/transfers, /admin/date

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.stop();
            monthly.shutdownNow();
            fx.close();
            app.stop();
        }));
        app.start(port);

        System.out.println();
        System.out.println("  Money Home is running:  http://localhost:" + port + "/index.html");
        System.out.println("  PIN step: " + (usePin ? "ON (first PIN entered by a phone becomes its PIN)" : "OFF"));
        if (generatedKey) System.out.println("  ADMIN KEY (random, this run only): " + adminKey);
        System.out.println();
    }

    private static int intEnv(String name, int fallback) {
        try { return Integer.parseInt(System.getenv(name)); } catch (RuntimeException e) { return fallback; }
    }

    private static String randomKey() {
        String alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        return sb.toString();
    }
}