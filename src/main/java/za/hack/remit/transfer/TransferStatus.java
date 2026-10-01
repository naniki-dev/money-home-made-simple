package za.hack.remit.transfer;

import java.util.Optional;

public enum TransferStatus {
    SENT, IN_TRANSIT, READY_TO_COLLECT, COLLECTED, CANCELLED, FAILED;

    /** Is moving from this status to `next` allowed? */
    public boolean canMoveTo(TransferStatus next) {
        return switch (this) {
            case SENT -> next == IN_TRANSIT || next == CANCELLED || next == FAILED;
            case IN_TRANSIT -> next == READY_TO_COLLECT || next == FAILED;
            case READY_TO_COLLECT -> next == COLLECTED;
            case COLLECTED, CANCELLED, FAILED -> false;
        };
    }

    /** The next step when everything goes well (used by the scheduler and admin button). */
    public Optional<TransferStatus> nextOnHappyPath() {
        return switch (this) {
            case SENT -> Optional.of(IN_TRANSIT);
            case IN_TRANSIT -> Optional.of(READY_TO_COLLECT);
            case READY_TO_COLLECT -> Optional.of(COLLECTED);
            default -> Optional.empty();
        };
    }

    /** No further moves possible. */
    public boolean isTerminal() {
        return this == COLLECTED || this == CANCELLED || this == FAILED;
    }
}