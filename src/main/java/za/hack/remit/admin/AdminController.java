package za.hack.remit.admin;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import za.hack.remit.notify.SmsSimulator;
import za.hack.remit.transfer.Transfer;
import za.hack.remit.transfer.TransferService;

public class AdminController {

    // RM- plus six characters from the same alphabet newReference() uses
    private static final Pattern REFERENCE = Pattern.compile("^RM-[A-HJKMNP-Z2-9]{6}$");

    private final TransferService transfers;
    private final SmsSimulator sms;
    private final byte[] adminKey; // null when no key is configured

    public AdminController(TransferService transfers, SmsSimulator sms, String adminKey) {
        this.transfers = transfers;
        this.sms = sms;
        this.adminKey = (adminKey == null || adminKey.isBlank())
                ? null
                : adminKey.getBytes(StandardCharsets.UTF_8);
    }

    public void register(Javalin app) {
        app.post("/admin/advance", this::advance);
        app.get("/api/sms", this::smsList);
    }

    private void advance(Context ctx) {
        if (adminKey == null) {
            ctx.status(503).result("Admin is disabled: no admin key is configured");
            return;
        }
        String supplied = ctx.header("X-Admin-Key");
        if (supplied == null
                || !MessageDigest.isEqual(adminKey, supplied.getBytes(StandardCharsets.UTF_8))) {
            ctx.status(401).result("Unauthorised");
            return;
        }
        String reference = ctx.formParam("reference");
        if (reference == null || !REFERENCE.matcher(reference.trim().toUpperCase()).matches()) {
            ctx.status(400).result("A valid reference is required");
            return;
        }
        try {
            Transfer t = transfers.advance(reference);
            ctx.json(Map.of("reference", t.getReference(), "status", t.getStatus().name()));
        } catch (NoSuchElementException e) {
            ctx.status(404).result("Transfer not found");
        } catch (IllegalStateException e) {
            ctx.status(409).result("Transfer cannot move forward");
        }
    }

    private void smsList(Context ctx) {
        List<Map<String, String>> out = sms.recent().stream()
                .map(m -> Map.of(
                        "sentAt", m.sentAt().toString(),
                        "to", m.toMasked(),
                        "text", m.text()))
                .toList();
        ctx.json(out);
    }
}
