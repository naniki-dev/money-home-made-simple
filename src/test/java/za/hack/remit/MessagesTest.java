package za.hack.remit;

import org.junit.jupiter.api.Test;

import java.text.MessageFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {
    // Worst-case argument values: long numbers fill the screen the most.
    private static final Object[] WORST = {"99999.99", "99999.99", "0.0555", "9999.99", "ABCDEF123456"};

    private ResourceBundle bundle(String lang) {
        return ResourceBundle.getBundle("messages", new Locale(lang));
    }

    @Test
    void everyEnglishScreenFitsIn160Characters() {
        ResourceBundle b = bundle("en");
        for (String key : b.keySet()) {
            String text = "CON " + MessageFormat.format(b.getString(key), WORST);
            assertTrue(text.length() <= 160, key + " is " + text.length() + " chars: " + text);
        }
    }

    @Test
    void everyShonaScreenFitsIn160Characters() {
        ResourceBundle b = bundle("sn");
        for (String key : b.keySet()) {
            String text = "CON " + MessageFormat.format(b.getString(key), WORST);
            assertTrue(text.length() <= 160, key + " is " + text.length() + " chars: " + text);
        }
    }

    @Test
    void shonaHasExactlyTheSameKeysAsEnglish() {
        Set<String> en = new TreeSet<>(bundle("en").keySet());
        Set<String> sn = new TreeSet<>(bundle("sn").keySet());
        assertEquals(en, sn, "Missing or extra Shona keys");
    }

    @Test
    void noMessageContainsAnUnescapedApostropheThatMessageFormatWouldEat() {
        ResourceBundle b = bundle("en");
        for (String key : b.keySet()) {
            String raw = b.getString(key);
            if (raw.contains("{")) {
                assertFalse(raw.replace("''", "").contains("'"), key + " has a single apostrophe");
            }
        }
    }
}