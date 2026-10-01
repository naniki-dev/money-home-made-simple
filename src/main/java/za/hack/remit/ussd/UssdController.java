package za.hack.remit.ussd;

import za.hack.remit.fees.QuoteCalculator;
import za.hack.remit.i18n.Messages;
import za.hack.remit.recurring.RecurringService;
import za.hack.remit.transfer.TransferService;
import za.hack.remit.ussd.UssdSession.Step;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UssdController {
    private static final long RESUME_WINDOW_MS = 10 * 60 * 1000;
    private static final BigDecimal MIN = new BigDecimal("50"), MAX = new BigDecimal("5000");

    private final Map<String, UssdSession> sessions = new ConcurrentHashMap<>();
    private final TransferService transfers;
    private final QuoteCalculator quotes;
    private final RecurringService recurring;

    public UssdController(TransferService t, QuoteCalculator q, RecurringService r) {
        this.transfers = t; this.quotes = q; this.recurring = r;
    }

    /** text = Africa's Talking style "1*500*1". We only use the LAST segment; state lives in the session. */
    public String handle(String sessionId, String phone, String text) {
        UssdSession s = sessions.computeIfAbsent(phone, p -> new UssdSession());
        String input = lastSegment(text);

        if (text == null || text.isEmpty()) {                 // fresh dial
            if (s.lang == null) s.step = Step.LANG;
            else if (s.hasDraft() && s.isFresh(RESUME_WINDOW_MS)) s.step = Step.RESUME;
            else { s.clearDraft(); s.step = Step.MENU; }
            return render(s, phone);
        }
        s.updatedAt = System.currentTimeMillis();
        return process(s, phone, sessionId, input);
    }

    private String process(UssdSession s, String phone, String sessionId, String in) {
        switch (s.step) {
            case LANG -> {
                if (in.equals("1")) s.lang = "en";
                else if (in.equals("2")) s.lang = "sn";
                else return "CON " + Messages.t("en", "err.invalid") + "\n" + render(s, phone).substring(4);
                s.step = Step.MENU;
            }
            case RESUME -> {
                if (in.equals("1")) s.step = Step.SEND_CONFIRM;
                else { s.clearDraft(); s.step = Step.MENU; }
            }
            case MENU -> {
                switch (in) {
                    case "1" -> s.step = s.recipient == null ? Step.SEND_RECIPIENT : Step.SEND_AMOUNT;
                    case "2" -> s.step = Step.STATUS_REF;
                    case "3" -> s.step = Step.AUTO_MENU;
                    case "0" -> { s.lang = null; s.step = Step.LANG; }
                    default -> { return "CON " + Messages.t(s.lang, "err.invalid") + "\n" + render(s, phone).substring(4); }
                }
            }
            case SEND_RECIPIENT -> {
                if (!in.matches("\\+?263\\d{9}|0\\d{9}")) return err(s, phone, "err.phone");
                s.recipient = in; s.step = Step.SEND_AMOUNT;
            }
            case SEND_AMOUNT -> {
                BigDecimal a = parseAmount(in);
                if (a == null) return err(s, phone, "err.amount");
                s.amount = a; s.draftKey = UUID.randomUUID().toString(); s.step = Step.SEND_CONFIRM;
            }
            case SEND_CONFIRM -> {
                if (!in.equals("1")) { s.clearDraft(); s.step = Step.MENU; break; }
                var t = transfers.create(s.draftKey, phone, s.recipient, s.amount, quotes.quote(s.amount));
                s.clearDraft(); s.step = Step.MENU;
                return "END " + Messages.t(s.lang, "send.done", t.ref());
            }
            case STATUS_REF -> {
                s.step = Step.MENU;
                return "END " + transfers.find(in)
                        .map(t -> Messages.t(s.lang, "status." + t.status().name(), t.ref()))
                        .orElse(Messages.t(s.lang, "status.notfound"));
            }
            case AUTO_MENU -> {
                switch (in) {
                    case "1" -> { if (s.recipient == null) return err(s, phone, "auto.needfirst"); s.step = Step.AUTO_AMOUNT; }
                    case "2" -> { s.step = Step.MENU;
                        return "END " + Messages.t(s.lang, recurring.skipNext(phone, LocalDate.now()) ? "auto.skipped" : "auto.none"); }
                    case "3" -> { recurring.cancel(phone); s.step = Step.MENU; return "END " + Messages.t(s.lang, "auto.cancelled"); }
                    default -> { return err(s, phone, "err.invalid"); }
                }
            }
            case AUTO_AMOUNT -> {
                BigDecimal a = parseAmount(in);
                if (a == null) return err(s, phone, "err.amount");
                s.autoAmount = a; s.step = Step.AUTO_DAY;
            }
            case AUTO_DAY -> {
                int d; try { d = Integer.parseInt(in); } catch (NumberFormatException e) { d = 0; }
                if (d < 1 || d > 28) return err(s, phone, "err.day");
                s.autoDay = d; s.step = Step.AUTO_CONFIRM;
            }
            case AUTO_CONFIRM -> {
                if (in.equals("1")) {
                    recurring.setup(phone, s.recipient, s.autoAmount, s.autoDay);
                    s.clearDraft(); s.step = Step.MENU;
                    return "END " + Messages.t(s.lang, "auto.done", s.autoDay);
                }
                s.clearDraft(); s.step = Step.MENU;
            }
        }
        return render(s, phone);
    }

    /** Renders the screen for the current step. Every string must stay <= 160 chars (see i18n test). */
    private String render(UssdSession s, String phone) {
        String l = s.lang;
        return switch (s.step) {
            case LANG -> "CON " + Messages.t("en", "lang.menu");
            case MENU -> "CON " + Messages.t(l, "menu.main");
            case RESUME -> "CON " + Messages.t(l, "resume", s.amount);
            case SEND_RECIPIENT -> "CON " + Messages.t(l, "send.recipient");
            case SEND_AMOUNT -> "CON " + Messages.t(l, "send.amount", MIN, MAX);
            case SEND_CONFIRM -> {
                var q = quotes.quote(s.amount);
                yield "CON " + Messages.t(l, "send.confirm", q.totalZar(), q.feeZar(), q.rate(), q.receiveUsd());
            }
            case STATUS_REF -> "CON " + Messages.t(l, "status.ask");
            case AUTO_MENU -> "CON " + Messages.t(l, "auto.menu");
            case AUTO_AMOUNT -> "CON " + Messages.t(l, "auto.amount");
            case AUTO_DAY -> "CON " + Messages.t(l, "auto.day");
            case AUTO_CONFIRM -> "CON " + Messages.t(l, "auto.confirm", s.autoAmount, s.autoDay);
        };
    }

    private String err(UssdSession s, String phone, String key) {
        return "CON " + Messages.t(s.lang, key) + "\n" + render(s, phone).substring(4);
    }

    private BigDecimal parseAmount(String in) {
        try {
            BigDecimal a = new BigDecimal(in.trim());
            return a.compareTo(MIN) >= 0 && a.compareTo(MAX) <= 0 ? a : null;
        } catch (NumberFormatException e) { return null; }
    }

    private String lastSegment(String text) {
        if (text == null || text.isEmpty()) return "";
        String[] p = text.split("\\*", -1);
        return p[p.length - 1].trim();
    }
}