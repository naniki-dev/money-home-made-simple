package za.hack.remit.ussd.screens;

import za.hack.remit.fees.Quote;
import za.hack.remit.i18n.Messages;
import za.hack.remit.transfer.Transfer;
import za.hack.remit.transfer.TransferService;
import za.hack.remit.ussd.ScreenId;
import za.hack.remit.ussd.UssdSession;

/** Creates the transfer. Used by ConfirmScreen (no PIN) or PinScreen (PIN on). */
public class SendAction {
    private final TransferService transfers;

    public SendAction(Messages messages, TransferService transfers) { this.transfers = transfers; }

    ScreenId send(UssdSession s) {
        Quote q = s.quote;
        // draftKey is the idempotency key: a resumed or double-tapped confirm returns the SAME transfer.
        Transfer t = transfers.create(s.get("draftKey"), s.msisdn(), s.recipient, s.recipient, s.lang(),
                q.amountZar(), q.feeZar(), q.totalZar(), q.rate(), q.receiverGetsUsd());
        s.lastReference = t.getReference();
        s.put("exitKey", "done");
        s.put("exitArg1", t.getReference());
        return ScreenId.EXIT;
    }
}
