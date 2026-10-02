package za.hack.remit.notify;

import java.util.Optional;
import za.hack.remit.transfer.Transfer;
import za.hack.remit.transfer.TransferStatus;

//TODO:
//transfers.addListener(new SenderStatusNotifier(sms, StatusMessages::forSender));
//transfers.addListener(new ReceiverStatusNotifier(sms, StatusMessages::forReceiver));
/** Temporary English wording. Person 5's Messages class replaces this. */
public final class StatusMessages {
    private StatusMessages() {}

    public static Optional<String> forSender(Transfer t, TransferStatus to) {
        String name = shortName(t.getRecipientName());
        String ref = t.getReference();
        String usd = "US$" + t.getReceiveAmountUsd().toPlainString();
        return Optional.of(switch (to) {
            case SENT -> "Sent R" + t.getAmountZar().toPlainString() + " to " + name
                    + ". Fee R" + t.getFeeZar().toPlainString()
                    + ". Rate " + t.getRate().toPlainString() + "/US$1. You pay R"
                    + t.getTotalZar().toPlainString() + ". " + name + " gets " + usd
                    + ". Ref " + ref;
            case IN_TRANSIT -> "Your money to " + name + " is on its way. Ref " + ref;
            case READY_TO_COLLECT -> name + " can collect " + usd + " now. Ref " + ref;
            case COLLECTED -> name + " collected " + usd + ". Ref " + ref;
            case CANCELLED -> "Transfer cancelled. Ref " + ref;
            case FAILED -> "Transfer failed. Ref " + ref + ". Try again or contact support.";
        });
    }

    public static Optional<String> forReceiver(Transfer t, TransferStatus to) {
        String usd = "US$" + t.getReceiveAmountUsd().toPlainString();
        return switch (to) {
            case READY_TO_COLLECT ->
                    Optional.of("Your money is ready to collect. " + usd + ". Ref " + t.getReference());
            case COLLECTED ->
                    Optional.of("You collected " + usd + ". Ref " + t.getReference()
                            + ". Not you? Contact support.");
            default -> Optional.empty();
        };
    }

    private static String shortName(String name) {
        String n = name.trim();
        return n.length() > 15 ? n.substring(0, 15) : n;
    }
}