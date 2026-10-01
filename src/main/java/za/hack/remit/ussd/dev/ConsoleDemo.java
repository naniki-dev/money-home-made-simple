package za.hack.remit.ussd.dev;

import java.util.NoSuchElementException;
import java.util.Scanner;
import za.hack.remit.fees.DefaultQuoteCalculator;
import za.hack.remit.fx.MockFxService;
import za.hack.remit.i18n.Messages;
import za.hack.remit.transfer.TransferService;
import za.hack.remit.ussd.*;

/** Click through the flow in the IntelliJ terminal. Empty line = re-dial *120#, "exit" quits. */
public class ConsoleDemo {
    public static void main(String[] args) {
        UssdController c = UssdFactory.create(new Messages(), new MockFxService(),
                new DefaultQuoteCalculator(), new TransferService(), null, key -> true);   // null = no PIN step
        String phone = "+27821234567";
        Scanner in = new Scanner(System.in);
        String text = "";
        String typed = "";
        int n = 0;
        while (true) {
            UssdResponse r = c.handleResponse("console-" + n, phone, text);
            System.out.println("\n[" + (r.fits() ? "ok " : "TOO LONG ") + r.text().length() + " chars]");
            System.out.println(r.toGatewayString());
            if (r.end()) System.out.println("-- session ended; press Enter to re-dial --");
            System.out.print("> ");
            try { typed = in.nextLine(); } catch (NoSuchElementException e) { return; }
            if (typed.equalsIgnoreCase("exit")) return;
            if (typed.isEmpty() || r.end()) { n++; text = ""; }
            else text = text.isEmpty() ? typed : text + "*" + typed;    // cumulative, like a real gateway
        }
    }
}
