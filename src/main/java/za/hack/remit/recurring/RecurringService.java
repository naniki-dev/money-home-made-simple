package za.hack.remit.recurring;

import za.hack.remit.fees.Quote;
import za.hack.remit.fees.QuoteCalculator;
import za.hack.remit.fees.QuoteException;
import za.hack.remit.fx.FxService;
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
    private final FxService fx;

    public RecurringService(TransferService t, QuoteCalculator q, SmsSimulator s, FxService fx) {
        this.transfers = t; this.quotes = q; this.sms = s; this.fx = fx;
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

    /** The send date in a month. Day 29 to 31 becomes the last day of shorter months. */
    private static LocalDate sendDateIn(YearMonth ym, int sendDay) {
        return ym.atDay(Math.min(sendDay, ym.lengthOfMonth()));
    }

    private YearMonth nextOccurrence(RecurringPlan p, LocalDate today) {
        YearMonth ym = YearMonth.from(today);
        boolean stillComingThisMonth = !today.isAfter(sendDateIn(ym, p.sendDay)) && !ym.equals(p.sentMonth);
        return stillComingThisMonth ? ym : ym.plusMonths(1);
    }

    /** A fresh rate lock every time, because a recurring send happens later and old locks expire. */
    private Quote freshQuote(BigDecimal zar) {
        return quotes.quote(zar, fx.lockRate("ZAR", "USD"));
    }

    /** Call from the scheduler (and from /admin/advance with a fake "today" for the demo). */
    public void tick(LocalDate today) {
        YearMonth ym = YearMonth.from(today);
        LocalDate tomorrow = today.plusDays(1);
        YearMonth tomorrowYm = YearMonth.from(tomorrow);

        for (RecurringPlan p : plans.values()) {
            if (!p.active) continue;
            try {
                // Day before: remind and let them skip. Uses tomorrow's month, so a plan on the 1st still works.
                if (tomorrow.equals(sendDateIn(tomorrowYm, p.sendDay))
                        && !tomorrowYm.equals(p.remindedMonth)
                        && !tomorrowYm.equals(p.skippedMonth)
                        && !tomorrowYm.equals(p.sentMonth)) {

                    Quote q = freshQuote(p.amountZar);
                    sms.send(p.senderPhone,
                            "Remit: R" + p.amountZar + " goes home tomorrow (est. $" + q.receiverGetsUsd()
                                    + "). To skip dial *120*3#");
                    p.remindedMonth = tomorrowYm;
                }

                // Send day: go unless skipped. Idempotency key = one send per plan per month.
                if (today.equals(sendDateIn(ym, p.sendDay))
                        && !ym.equals(p.skippedMonth) && !ym.equals(p.sentMonth)) {
                    Quote q = freshQuote(p.amountZar);
                    transfers.create("recurring:" + p.senderPhone + ":" + ym,
                            p.senderPhone, p.recipientPhone, p.recipientPhone, "en",
                            q.amountZar(), q.feeZar(), q.totalZar(), q.rate(), q.receiverGetsUsd());
                    p.sentMonth = ym;
                    sms.send(p.senderPhone, "Remit: your monthly R" + p.amountZar + " was sent.");
                }
            } catch (QuoteException e) {
                // e.g. amount outside the limits: skip this plan, keep the others running
                System.err.println("Recurring skipped for " + p.senderPhone + ": " + e.messageKey());
            }
        }
    }
}