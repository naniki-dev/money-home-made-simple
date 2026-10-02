package za.hack.remit.transfer;

@FunctionalInterface
public interface TransferListener {
    /** `from` is null when the transfer has just been created. */
    void onStatusChanged(Transfer transfer, TransferStatus from, TransferStatus to);
}

