package za.hack.remit.notify;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SmsSimulatorTest {

    private static final String TO = "+263771234567";

    @Test
    void rejectsABlankNumberOrBlankText() {
        SmsSimulator sms = new SmsSimulator();
        assertThrows(IllegalArgumentException.class, () -> sms.send(null, "hi"));
        assertThrows(IllegalArgumentException.class, () -> sms.send(" ", "hi"));
        assertThrows(IllegalArgumentException.class, () -> sms.send(TO, null));
        assertThrows(IllegalArgumentException.class, () -> sms.send(TO, "   "));
        assertTrue(sms.recent().isEmpty());
    }

    @Test
    void rejectsTextOverOneHundredAndSixtyCharacters() {
        SmsSimulator sms = new SmsSimulator();
        assertDoesNotThrow(() -> sms.send(TO, "x".repeat(160)));
        assertThrows(IllegalArgumentException.class, () -> sms.send(TO, "x".repeat(161)));
        assertEquals(1, sms.recent().size());
    }

    @Test
    void stripsControlCharactersButKeepsNewlines() {
        SmsSimulator sms = new SmsSimulator();
        assertEquals("Hi there", sms.send(TO, "Hi\u0007 there").text());
        assertEquals("a\nb", sms.send(TO, "a\nb").text());
    }

    @Test
    void masksTheNumber() {
        assertEquals("+263******567", SmsSimulator.mask("+263771234567"));
        assertEquals("****", SmsSimulator.mask("12345"));
    }

    @Test
    void neverStoresTheFullNumber() {
        SmsSimulator sms = new SmsSimulator();
        SmsSimulator.SmsMessage m = sms.send(TO, "hi");
        assertEquals("+263******567", m.toMasked());
        assertFalse(sms.recent().toString().contains("771234567"));
    }

    @Test
    void keepsOnlyTheNewestFiftyNewestFirst() {
        SmsSimulator sms = new SmsSimulator();
        for (int i = 0; i < 60; i++) {
            sms.send(TO, "m" + i);
        }
        assertEquals(50, sms.recent().size());
        assertEquals("m59", sms.recent().get(0).text());
        assertEquals("m10", sms.recent().get(49).text());
    }

    @Test
    void recentReturnsACopy() {
        SmsSimulator sms = new SmsSimulator();
        sms.send(TO, "hi");
        sms.recent().clear();
        assertEquals(1, sms.recent().size());
    }
}
