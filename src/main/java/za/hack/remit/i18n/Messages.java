package za.hack.remit.i18n;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/** Loads messages_<lang>.properties (UTF-8), falls back to English. Uses {0} placeholders (MessageFormat). */
public class Messages {
    private final Map<String, Properties> cache = new ConcurrentHashMap<>();

    public String get(String lang, String key, Object... args) {
        String t = load(lang).getProperty(key);
        if (t == null) t = load("en").getProperty(key, "??" + key + "??");
        return MessageFormat.format(t, args);
    }

    private Properties load(String lang) {
        return cache.computeIfAbsent(lang, l -> {
            Properties p = new Properties();
            try (InputStream in = Messages.class.getResourceAsStream("/messages_" + l + ".properties")) {
                if (in != null) p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
            return p;
        });
    }
}
