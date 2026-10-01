package za.hack.remit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UssdControllerTest {
    private Fixture f;

    @BeforeEach
    void setUp() { f = new Fixture(); }

    /** Walks a brand-new user to the confirm screen for R500. */
    private String reachConfirmScreen() {
        f.freshDial();
        f.dial("1");                                   // English
        f.dial("1", "1");                              // Send money
        f.dial("1", "1", "0771234567");                // receiver
        return f.dial("1", "1", "0771234567", "500");  // amount -> confirm screen
    }

    @Test
    void firstDialAsksForLanguage() {
        String screen = f.freshDial();
        assertTrue(screen.startsWith("CON"));
        assertTrue(screen.contains("English") && screen.contains("Shona"));
    }

    @Test
    void confirmScreenShowsFeeRateAndWhatTheyGet() {
        String screen = reachConfirmScreen();
        assertTrue(screen.contains("Fee"), screen);
        assertTrue(screen.contains("Rate"), screen);
        assertTrue(screen.contains("$"), screen);
        assertTrue(screen.length() <= 160);
    }

    @Test
    void fullSendCreatesExactlyOneTransferAndEndsSession() {
        reachConfirmScreen();
        String end = f.dial("1", "1", "0771234567", "500", "1");
        assertTrue(end.startsWith("END"), end);
        assertEquals(1, f.transfers.all().size());
    }

    @Test
    void invalidAmountShowsErrorAndAsksAgain() {
        f.freshDial(); f.dial("1"); f.dial("1", "1"); f.dial("1", "1", "0771234567");
        String screen = f.dial("1", "1", "0771234567", "10");   // below minimum
        assertTrue(screen.startsWith("CON"));
        assertTrue(screen.contains("Invalid amount"), screen);
        assertEquals(0, f.transfers.all().size());
    }

    @Test
    void invalidPhoneNumberIsRejected() {
        f.freshDial(); f.dial("1"); f.dial("1", "1");
        String screen = f.dial("1", "1", "12345");
        assertTrue(screen.contains("Invalid number"), screen);
    }

    @Test
    void droppedSessionOffersResumeAndDoesNotSendTwice() {
        reachConfirmScreen();                           // ...then the signal dies here

        String resume = f.freshDial();                  // dial *120# again
        assertTrue(resume.contains("in progress"), resume);
        assertTrue(resume.contains("500"), resume);

        String confirm = f.ussd.handle("sess-3", Fixture.PHONE, "1");   // 1 = Continue
        assertTrue(confirm.contains("Fee"), confirm);

        String end = f.ussd.handle("sess-3", Fixture.PHONE, "1*1");     // 1 = Send
        assertTrue(end.startsWith("END"));
        assertEquals(1, f.transfers.all().size(), "resume must not create a second transfer");
    }

    @Test
    void cancellingResumeClearsTheDraft() {
        reachConfirmScreen();
        f.freshDial();
        String menu = f.ussd.handle("sess-3", Fixture.PHONE, "2");      // 2 = Cancel
        assertTrue(menu.contains("Send money"), menu);
        assertEquals(0, f.transfers.all().size());
    }

    @Test
    void secondSendReusesSavedReceiverSoItSkipsThePhoneNumberScreen() {
        reachConfirmScreen();
        f.dial("1", "1", "0771234567", "500", "1");     // first send done

        f.freshDial();
        String screen = f.ussd.handle("sess-4", Fixture.PHONE, "1");    // Send money
        assertTrue(screen.contains("Amount"), "should go straight to amount: " + screen);
    }

    @Test
    void shonaIsRememberedPerPhoneNumber() {
        f.freshDial();
        f.dial("2");                                    // Shona
        String next = f.freshDial();                    // dial again
        assertFalse(next.contains("Ririmi"), "language menu should not show again: " + next);
    }
}