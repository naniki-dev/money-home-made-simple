package za.hack.remit.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransferStatusTest {

    private static final Set<String> LEGAL = Set.of(
            "SENT>IN_TRANSIT", "SENT>CANCELLED", "SENT>FAILED",
            "IN_TRANSIT>READY_TO_COLLECT", "IN_TRANSIT>FAILED",
            "READY_TO_COLLECT>COLLECTED");

    @Test
    void aMoveIsLegalOnlyIfItIsListed() {
        for (TransferStatus from : TransferStatus.values()) {
            for (TransferStatus to : TransferStatus.values()) {
                assertEquals(LEGAL.contains(from + ">" + to), from.canMoveTo(to), from + " to " + to);
            }
        }
    }

    @Test
    void theHappyPathWalksInOrderThenStops() {
        assertEquals(Optional.of(TransferStatus.IN_TRANSIT), TransferStatus.SENT.nextOnHappyPath());
        assertEquals(Optional.of(TransferStatus.READY_TO_COLLECT), TransferStatus.IN_TRANSIT.nextOnHappyPath());
        assertEquals(Optional.of(TransferStatus.COLLECTED), TransferStatus.READY_TO_COLLECT.nextOnHappyPath());
        assertTrue(TransferStatus.COLLECTED.nextOnHappyPath().isEmpty());
        assertTrue(TransferStatus.CANCELLED.nextOnHappyPath().isEmpty());
        assertTrue(TransferStatus.FAILED.nextOnHappyPath().isEmpty());
    }

    @Test
    void onlyTheThreeEndStatusesAreTerminal() {
        assertTrue(TransferStatus.COLLECTED.isTerminal());
        assertTrue(TransferStatus.CANCELLED.isTerminal());
        assertTrue(TransferStatus.FAILED.isTerminal());
        assertFalse(TransferStatus.SENT.isTerminal());
        assertFalse(TransferStatus.IN_TRANSIT.isTerminal());
        assertFalse(TransferStatus.READY_TO_COLLECT.isTerminal());
    }
}
