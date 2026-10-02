package za.hack.remit.notify;

import java.util.Optional;
import java.util.function.BiFunction;
import za.hack.remit.transfer.Transfer;
import za.hack.remit.transfer.TransferListener;
import za.hack.remit.transfer.TransferStatus;

public class SenderStatusNotifier implements TransferListener {

    private final SmsSimulator sms;
    private final BiFunction<Transfer, TransferStatus, Optional<String>> textFor;

    public SenderStatusNotifier(SmsSimulator sms,
                                BiFunction<Transfer, TransferStatus, Optional<String>> textFor) {
        this.sms = sms;
        this.textFor = textFor;
    }

    @Override
    public void onStatusChanged(Transfer transfer, TransferStatus from, TransferStatus to) {
        textFor.apply(transfer, to)
                .ifPresent(text -> sms.send(transfer.getSenderPhone(), text));
    }
}