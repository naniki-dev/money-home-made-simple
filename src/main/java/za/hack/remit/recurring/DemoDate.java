package za.hack.remit.recurring;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * "Today" for the monthly auto-send. In a demo you cannot wait a month, so the admin panel
 * can push this date forward day by day. Everything date-related asks this class, never LocalDate.now().
 */
public class DemoDate {
    private final Clock clock;
    private volatile int offsetDays = 0;

    public DemoDate() { this(Clock.system(ZoneId.of("Africa/Johannesburg"))); }
    public DemoDate(Clock clock) { this.clock = clock; }

    public LocalDate today() { return LocalDate.now(clock).plusDays(offsetDays); }

    public synchronized LocalDate advance(int days) {
        offsetDays += days;
        return today();
    }
}
