package za.hack.remit.ussd.dev;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import za.hack.remit.fees.DefaultQuoteCalculator;
import za.hack.remit.fx.MockFxService;
import za.hack.remit.i18n.Messages;
import za.hack.remit.notify.ReceiverStatusNotifier;
import za.hack.remit.notify.SenderStatusNotifier;
import za.hack.remit.notify.SmsSimulator;
import za.hack.remit.notify.StatusMessages;
import za.hack.remit.transfer.TransferScheduler;
import za.hack.remit.transfer.TransferService;
import za.hack.remit.ussd.*;

/** Console demo with status changes. Empty line = re-dial, "exit" quits, !adv and !sms are commands. */
public class ConsoleDemo {

    /** Seconds between automatic status steps. 0 = manual only (use !adv). */
    private static final int AUTO_SECONDS = 20;

    public static void main(String[] args) {
        SmsSimulator sms = new SmsSimulator();
        TransferService transfers = new TransferService();
        transfers.addListener(new SenderStatusNotifier(sms, StatusMessages::forSender));
        transfers.addListener(new ReceiverStatusNotifier(sms, StatusMessages::forReceiver));

        // Remembers the newest reference and prints every status change.
        String[] lastRef = new String[1];
        transfers.addListener((t, from, to) -> {
            if (from == null) lastRef[0] = t.getReference();
            System.out.println("\n  [status] " + t.getReference() + ": "
                    + (from == null ? "created" : from) + " -> " + to);
        });

        if (AUTO_SECONDS > 0) {
            new TransferScheduler(transfers, Duration.ofSeconds(AUTO_SECONDS)).start();
        }

        UssdController c = UssdFactory.create(new Messages(), new MockFxService(),
                new DefaultQuoteCalculator(), transfers, null, key -> true);   // null = no PIN step

        String phone = "+27821234567";
        Scanner in = new Scanner(System.in);
        String text = "";
        int n = 0;
        while (true) {
            UssdResponse r = c.handleResponse("console-" + n, phone, text);
            System.out.println("\n[" + (r.fits() ? "ok " : "TOO LONG ") + r.text().length() + " chars]");
            System.out.println(r.toGatewayString());
            if (r.end()) System.out.println("-- session ended; press Enter to re-dial --");

            String typed;
            while (true) {
                System.out.print("> ");
                try { typed = in.nextLine(); } catch (NoSuchElementException e) { return; }
                if (!typed.startsWith("!")) break;
                runCommand(typed, transfers, sms, lastRef);   // commands don't re-draw the screen
            }
            if (typed.equalsIgnoreCase("exit")) return;
            if (typed.isEmpty() || r.end()) { n++; text = ""; }
            else text = text.isEmpty() ? typed : text + "*" + typed;
        }
    }

    private static void runCommand(String line, TransferService transfers, SmsSimulator sms, String[] lastRef) {
        String[] parts = line.trim().split("\\s+");
        switch (parts[0].toLowerCase()) {
            case "!adv" -> {
                String ref = parts.length > 1 ? parts[1] : lastRef[0];
                if (ref == null) {
                    System.out.println("  No transfer yet. Confirm a send first.");
                    return;
                }
                try {
                    transfers.advance(ref);
                } catch (RuntimeException e) {
                    System.out.println("  Cannot advance: " + e.getMessage());
                }
            }
            case "!sms" -> {
                List<SmsSimulator.SmsMessage> all = sms.recent();
                if (all.isEmpty()) System.out.println("  (no SMS yet)");
                for (int i = all.size() - 1; i >= 0; i--) {
                    SmsSimulator.SmsMessage m = all.get(i);
                    System.out.println("  SMS to " + m.toMasked() + ": " + m.text());
                }
            }
            default -> System.out.println("  Commands: !adv [reference]   !sms");
        }
    }
}