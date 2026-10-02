package za.hack.remit.admin;

import static org.junit.jupiter.api.Assertions.*;

import io.javalin.Javalin;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import za.hack.remit.notify.ReceiverStatusNotifier;
import za.hack.remit.notify.SenderStatusNotifier;
import za.hack.remit.notify.SmsSimulator;
import za.hack.remit.notify.StatusMessages;
import za.hack.remit.transfer.Recipient;
import za.hack.remit.transfer.Terms;
import za.hack.remit.transfer.TransferService;
import za.hack.remit.transfer.TransferStatus;

class AdminControllerTest {

    private static final String KEY = "test-key-0123456789";
    private static final String SENDER = "+27821234567";
    private static final String RECEIVER = "+263771234567";

    private final HttpClient http = HttpClient.newHttpClient();
    private Javalin app;
    private int port;
    private TransferService transfers;
    private String reference;

    private void start(String configuredKey) {
        SmsSimulator sms = new SmsSimulator();
        transfers = new TransferService();
        transfers.addListener(new SenderStatusNotifier(sms, StatusMessages::forSender));
        transfers.addListener(new ReceiverStatusNotifier(sms, StatusMessages::forReceiver));
        reference = transfers.create("s1", SENDER, new Recipient("Mama", RECEIVER), "en",
                new Terms(new BigDecimal("500.00"), new BigDecimal("25.00"), new BigDecimal("525.00"),
                        new BigDecimal("17.80"), new BigDecimal("28.09"))).getReference();
        app = Javalin.create();
        new AdminController(transfers, sms, configuredKey).register(app);
        app.start(0);
        port = app.port();
    }

    @AfterEach
    void stop() {
        if (app != null) {
            app.stop();
        }
    }

    private HttpResponse<String> advance(String key, String formBody) throws Exception {
        HttpRequest.Builder b = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + "/admin/advance"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody));
        if (key != null) {
            b.header("X-Admin-Key", key);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private TransferStatus status() {
        return transfers.find(reference, SENDER).orElseThrow().getStatus();
    }

    @Test
    void noKeyConfiguredRefusesEveryone() throws Exception {
        start(null);
        assertEquals(503, advance(KEY, "reference=" + reference).statusCode());
        assertEquals(TransferStatus.SENT, status());
    }

    @Test
    void aBlankKeyConfiguredAlsoRefusesEveryone() throws Exception {
        start("   ");
        assertEquals(503, advance("   ", "reference=" + reference).statusCode());
        assertEquals(TransferStatus.SENT, status());
    }

    @Test
    void aMissingOrWrongKeyIsUnauthorised() throws Exception {
        start(KEY);
        assertEquals(401, advance(null, "reference=" + reference).statusCode());
        assertEquals(401, advance("WRONG", "reference=" + reference).statusCode());
        assertEquals(TransferStatus.SENT, status());
    }

    @Test
    void aBadReferenceIsRejected() throws Exception {
        start(KEY);
        assertEquals(400, advance(KEY, "reference=hello").statusCode());
        assertEquals(400, advance(KEY, "other=1").statusCode());
    }

    @Test
    void anUnknownReferenceIsNotFound() throws Exception {
        start(KEY);
        assertEquals(404, advance(KEY, "reference=RM-AAAAAA").statusCode());
    }

    @Test
    void aValidCallMovesTheTransferAndReturnsOnlyReferenceAndStatus() throws Exception {
        start(KEY);
        HttpResponse<String> r = advance(KEY, "reference=" + reference);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("IN_TRANSIT"));
        assertTrue(r.body().contains(reference));
        assertFalse(r.body().contains("771234567"));
        assertEquals(TransferStatus.IN_TRANSIT, status());
    }

    @Test
    void aFinishedTransferCannotMoveAgain() throws Exception {
        start(KEY);
        for (int i = 0; i < 3; i++) {
            assertEquals(200, advance(KEY, "reference=" + reference).statusCode());
        }
        assertEquals(409, advance(KEY, "reference=" + reference).statusCode());
        assertEquals(TransferStatus.COLLECTED, status());
    }

    @Test
    void theSmsListMasksEveryNumber() throws Exception {
        start(KEY);
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/sms")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("+278*****567"));
        assertTrue(r.body().contains(reference));
        assertFalse(r.body().contains("21234567"));
        assertFalse(r.body().contains("771234567"));
    }
}
