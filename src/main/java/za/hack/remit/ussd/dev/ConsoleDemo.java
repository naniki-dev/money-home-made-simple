package za.hack.remit.ussd.dev;

import java.util.NoSuchElementException;
import java.util.Scanner;
import za.hack.remit.i18n.Messages;
import za.hack.remit.stubs.Stubs;
import za.hack.remit.ussd.*;

/** Run in IntelliJ to click through the flow in the terminal. Type "exit" to quit, empty line = re-dial. */
public class ConsoleDemo {
    public static void main(String[] args) {
        UssdController c = UssdFactory.create(new Messages(), new Stubs.StubQuotes(), new Stubs.StubTransfers(),
                new Stubs.StubPins(), Stubs.ALLOW_ALL, Stubs.LIMITS);
        String msisdn = "+27821234567";
        Scanner in = new Scanner(System.in);
        String input = "";
        while (true) {
            UssdResponse r = c.handle(msisdn, "console-1", input);
            System.out.println("\n[" + (r.fits() ? "ok " : "TOO LONG ") + r.text().length() + " chars]");
            System.out.println(r.toGatewayString());
            if (r.end()) System.out.println("-- session ended; press Enter to re-dial --");
            System.out.print("> ");
            try { input = in.nextLine(); } catch (NoSuchElementException e) { return; }
            if (input.equalsIgnoreCase("exit")) return;
        }
    }
}
