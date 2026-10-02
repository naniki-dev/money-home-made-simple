package za.hack.remit.recurring;

import za.hack.remit.fees.QuoteCalculator;
import za.hack.remit.notify.SmsSimulator;
import za.hack.remit.transfer.TransferService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RecurringService {
    private final Map<String, RecurringPlan> plans = new ConcurrentHashMap<>(); // by sender phone
    private final TransferService transfers;
    private final QuoteCalculator quotes;
    private final SmsSimulator sms;

    public RecurringService(TransferService t, QuoteCalculator q, SmsSimulator s) {
        this.transfers = t; this.quotes = q; this.sms = s;
    }

    public RecurringPlan setup(String sender, String recipient, BigDecimal zar, int day) {
        RecurringPlan p = new RecurringPlan(sender, recipient, zar, day);
        plans.put(sender, p);
        return p;
    }

    public Optional<RecurringPlan> find(String sender) {
        return Optional.ofNullable(plans.get(sender)).filter(p -> p.active);
    }

    /** Sender chose "skip this month". Applies to the next occurrence. */
    public boolean skipNext(String sender, LocalDate today) {
        return find(sender).map(p -> {
            p.skippedMonth = nextOccurrence(p, today);
            return true;
        }).orElse(false);
    }

    public void cancel(String sender) { find(sender).ifPresent(p -> p.active = false); }

    public YearMonth nextOccurrenceMonth(String sender, LocalDate today) {
        return find(sender).map(p -> nextOccurrence(p, today)).orElse(null);
    }

    private YearMonth nextOccurrence(RecurringPlan p, LocalDate today) {
        YearMonth ym = YearMonth.from(today);
        return today.getDayOfMonth() <= p.sendDay && !ym.equals(p.sentMonth) ? ym : ym.plusMonths(1);
    }

    /** Call from the scheduler (and from /admin/advance with a fake "today" for the demo). */
    public void tick(LocalDate today) {
        YearMonth ym = YearMonth.from(today);
        for (RecurringPlan p : plans.values()) {
            if (!p.active) continue;

            // Day before: remind and let them skip.
            LocalDate sendDate = ym.atDay(p.sendDay);
            if (today.equals(sendDate.minusDays(1)) && !ym.equals(p.remindedMonth)
                    && !ym.equals(p.skippedMonth)) {
                var q = quotes.quote(p.amountZar, );
                sms.send(p.senderPhone,
                        "Remit: R" + p.amountZar + " goes home tomorrow (est. $" + q.receiveUsd()
                                + "). To skip dial *120*3#");
                p.remindedMonth = ym;
            }

            // Send day: go unless skipped. Idempotency key = one send per plan per month.
            if (today.equals(sendDate) && !ym.equals(p.skippedMonth) && !ym.equals(p.sentMonth)) {
                transfers.create("recurring:" + p.senderPhone + ":" + ym,
                        p.senderPhone, p.recipientPhone, p.amountZar, quotes.quote(p.amountZar));
                p.sentMonth = ym;
                sms.send(p.senderPhone, "Remit: your monthly R" + p.amountZar + " was sent.");
            }
        }
    }
}