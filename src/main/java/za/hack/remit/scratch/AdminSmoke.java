package za.hack.remit.scratch;

import io.javalin.Javalin;
import java.math.BigDecimal;
import za.hack.remit.admin.AdminController;
import za.hack.remit.notify.ReceiverStatusNotifier;
import za.hack.remit.notify.SenderStatusNotifier;
import za.hack.remit.notify.SmsSimulator;
import za.hack.remit.notify.StatusMessages;
import za.hack.remit.transfer.Transfer;
import za.hack.remit.transfer.TransferService;

public class AdminSmoke {
    static void main(String[] args) {
        SmsSimulator sms = new SmsSimulator();
        TransferService transfers = new TransferService();
        transfers.addListener(new SenderStatusNotifier(sms, StatusMessages::forSender));
        transfers.addListener(new ReceiverStatusNotifier(sms, StatusMessages::forReceiver));

        Transfer t = transfers.create("smoke-session-1", "+27821234567", "Mama",
                "+263771234567", "en",
                new BigDecimal("500.00"), new BigDecimal("25.00"), new BigDecimal("525.00"),
                new BigDecimal("17.80"), new BigDecimal("28.09"));
        System.out.println("TEST REFERENCE: " + t.getReference());

        Javalin app = Javalin.create();
        new AdminController(transfers, sms, System.getenv("REMIT_ADMIN_KEY")).register(app);
        app.start(7070);
    }
}