package za.hack.remit.recurring;

import java.math.BigDecimal;
import java.time.YearMonth;

public class RecurringPlan {
    public final String senderPhone;
    public final String recipientPhone;
    public final BigDecimal amountZar;
    public final int sendDay;            // 1..28 only, so every month has it
    public boolean active = true;
    public YearMonth skippedMonth;       // month the sender chose to skip
    public YearMonth remindedMonth;      // last month we sent the day-before SMS
    public YearMonth sentMonth;          // last month we actually sent

    public RecurringPlan(String senderPhone, String recipientPhone,
                         BigDecimal amountZar, int sendDay) {
        this.senderPhone = senderPhone;
        this.recipientPhone = recipientPhone;
        this.amountZar = amountZar;
        this.sendDay = Math.max(1, Math.min(28, sendDay));
    }
}