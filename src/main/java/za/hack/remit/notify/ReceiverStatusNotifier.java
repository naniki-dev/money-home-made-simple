package za.hack.remit.notify;

//TODO: Add in main
//SmsSimulator sms = new SmsSimulator();
//TransferService transfers = new TransferService();
//transfers.addListener(new ReadyToCollectNotifier(sms,
//        t -> "Your money is ready to collect. Ref " + t.getReference()
//           + ". US$" + t.getReceiveAmountUsd().toPlainString()));

//TODO: PROOF OF IDENTITY OF RECIPIENT DURING COLLECT


import java.util.Optional;
import java.util.function.BiFunction;
import za.hack.remit.transfer.Transfer;
import za.hack.remit.transfer.TransferListener;
import za.hack.remit.transfer.TransferStatus;

public class ReceiverStatusNotifier implements TransferListener {

    private final SmsSimulator sms;
    private final BiFunction<Transfer, TransferStatus, Optional<String>> textFor;

    public ReceiverStatusNotifier(SmsSimulator sms,
                                  BiFunction<Transfer, TransferStatus, Optional<String>> textFor) {
        this.sms = sms;
        this.textFor = textFor;
    }

    @Override
    public void onStatusChanged(Transfer transfer, TransferStatus from, TransferStatus to) {
        textFor.apply(transfer, to)
                .ifPresent(text -> sms.send(transfer.getRecipientPhone(), text));
    }
}