package za.hack.remit.admin;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import za.hack.remit.fx.FxService;
import za.hack.remit.recurring.DemoDate;
import za.hack.remit.recurring.RecurringService;
import za.hack.remit.security.Masker;
import za.hack.remit.transfer.TransferService;

/** Read-only rate endpoint, the admin transfer list, and the "move the calendar" demo button. */
public class DemoController {
    private final TransferService transfers;
    private final FxService fx;
    private final RecurringService recurring;
    private final DemoDate demoDate;
    private final byte[] adminKey;   // null = admin disabled

    public DemoController(TransferService transfers, FxService fx, RecurringService recurring,
                          DemoDate demoDate, String adminKey) {
        this.transfers = transfers;
        this.fx = fx;
        this.recurring = recurring;
        this.demoDate = demoDate;
        this.adminKey = (adminKey == null || adminKey.isBlank()) ? null : adminKey.getBytes(StandardCharsets.UTF_8);
    }

    public void register(Javalin app) {
        app.get("/api/rate", this::rate);
        app.get("/api/demo-date", ctx -> ctx.json(Map.of("date", demoDate.today().toString())));
        app.get("/api/transfers", this::listTransfers);
        app.post("/admin/date", this::advanceDate);
    }

    private void rate(Context ctx) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("rate", fx.currentRate("ZAR", "USD").toPlainString());          // what the customer gets
        out.put("midMarket", fx.midMarketRate("ZAR", "USD").toPlainString());   // the real market rate
        ctx.json(out);
    }

    private void listTransfers(Context ctx) {
        if (!authorised(ctx)) return;
        List<Map<String, String>> out = transfers.all().stream().map(t -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("reference", t.getReference());
            m.put("status", t.getStatus().name());
            m.put("amountZar", t.getAmountZar().toPlainString());
            m.put("receiveUsd", t.getReceiveAmountUsd().toPlainString());
            m.put("from", Masker.msisdn(t.getSenderPhone()));
            m.put("to", Masker.msisdn(t.getRecipientPhone()));
            return m;
        }).toList();
        ctx.json(out);
    }

    /** POST /admin/date  days=1 : moves the demo calendar forward and runs the monthly check at once. */
    private void advanceDate(Context ctx) {
        if (!authorised(ctx)) return;
        int days;
        try {
            days = Integer.parseInt(String.valueOf(ctx.formParam("days")).trim());
        } catch (NumberFormatException e) {
            days = 0;
        }
        if (days < 1 || days > 31) {
            ctx.status(400).result("days must be 1 to 31");
            return;
        }
        LocalDate today = demoDate.advance(days);
        recurring.tick(today);
        ctx.json(Map.of("date", today.toString()));
    }

    private boolean authorised(Context ctx) {
        if (adminKey == null) { ctx.status(503).result("Admin is disabled: no admin key is configured"); return false; }
        String supplied = ctx.header("X-Admin-Key");
        if (supplied == null || !MessageDigest.isEqual(adminKey, supplied.getBytes(StandardCharsets.UTF_8))) {
            ctx.status(401).result("Unauthorised");
            return false;
        }
        return true;
    }
}
