package za.hack.remit.transfer;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.time.Duration;
import java.time.Instant;
public class TransferService {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private final SecureRandom random = new SecureRandom();

    private final Map<String, Transfer> byReference = new ConcurrentHashMap<>();
    private final Map<String, String> referenceBySession = new ConcurrentHashMap<>();

    private static final Logger LOG = Logger.getLogger(TransferService.class.getName());
    private final List<TransferListener> listeners = new CopyOnWriteArrayList<>();
    /** Called when the customer presses Confirm. A repeat with the same sessionId returns the same transfer. */
    public Transfer create(String sessionId, String senderPhone, String recipientName,
                           String recipientPhone, String language,
                           BigDecimal amountZar, BigDecimal feeZar, BigDecimal totalZar,
                           BigDecimal rate, BigDecimal receiveAmountUsd) {

        requireText(sessionId, "sessionId");
        requireText(senderPhone, "senderPhone");
        requireText(recipientName, "recipientName");
        requireText(recipientPhone, "recipientPhone");
        requirePositive(amountZar, "amountZar");
        requirePositive(totalZar, "totalZar");
        requirePositive(rate, "rate");
        requirePositive(receiveAmountUsd, "receiveAmountUsd");
        if (feeZar == null || feeZar.signum() < 0) {
            throw new IllegalArgumentException("feeZar must not be negative");
        }

        AtomicBoolean created = new AtomicBoolean(false);
        String reference = referenceBySession.computeIfAbsent(sessionId, id -> {
            Transfer t;
            do {
                t = new Transfer(newReference(), sessionId, senderPhone, recipientName,
                        recipientPhone, language, amountZar, feeZar, totalZar, rate, receiveAmountUsd);
            } while (byReference.putIfAbsent(t.getReference(), t) != null);
            created.set(true);
            return t.getReference();
        });
        Transfer result = byReference.get(reference);
        if (created.get()) {
            notifyListeners(result, null, TransferStatus.SENT);
        }
        return result;
    }
    /** For the customer's "track money" screen: only returns the transfer to its own sender. */
    public Optional<Transfer> find(String reference, String requesterPhone) {
        return Optional.ofNullable(byReference.get(normalise(reference)))
                .filter(t -> t.isOwnedBy(requesterPhone));
    }

    /** Admin and scheduler use only. Never expose this to a customer screen. */
    Optional<Transfer> findForAdmin(String reference) {
        return Optional.ofNullable(byReference.get(normalise(reference)));
    }

    /** Every transfer, newest first. For the admin/demo panel only. Never expose this to a customer screen. */
    public List<Transfer> all() {
        return byReference.values().stream()
                .sorted(java.util.Comparator.comparing(Transfer::getCreatedAt).reversed())
                .toList();
    }

    /** Transfers that can still move forward. */
    List<Transfer> listActive() {
        return byReference.values().stream()
                .filter(t -> !t.getStatus().isTerminal())
                .toList();
    }

    /** Move one step along the normal path. */
    public Transfer advance(String reference) {
        Transfer t = getOrThrow(reference);
        synchronized (t) {
            TransferStatus next = t.getStatus().nextOnHappyPath()
                    .orElseThrow(() -> new IllegalStateException("Transfer cannot move forward"));
            moveTo(t, next);;
            return t;
        }
    }

    /** Move to a specific status (for example FAILED or CANCELLED), if the rules allow it. */
    public Transfer changeStatus(String reference, TransferStatus next) {
        Transfer t = getOrThrow(reference);
        synchronized (t) {
            if (!t.getStatus().canMoveTo(next)) {
                throw new IllegalStateException("Move not allowed: " + t.getStatus() + " to " + next);
            }
            moveTo(t, next);      // <-- this line, not t.setStatus(next)
            return t;
        }
    }

    private Transfer getOrThrow(String reference) {
        Transfer t = byReference.get(normalise(reference));
        if (t == null) throw new NoSuchElementException("Transfer not found");
        return t;
    }

    private String newReference() {
        StringBuilder sb = new StringBuilder("RM-");
        for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    private static String normalise(String reference) {
        return reference == null ? "" : reference.trim().toUpperCase();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    public void addListener(TransferListener listener) {
        listeners.add(listener);
    }

    /** Changes the status, then tells every listener. A failing listener never undoes the change. */
    private void moveTo(Transfer t, TransferStatus next) {
        TransferStatus from = t.getStatus();
        t.setStatus(next);
        notifyListeners(t, from, next);
    }

    private void notifyListeners(Transfer t, TransferStatus from, TransferStatus to) {
        for (TransferListener listener : listeners) {
            try {
                listener.onStatusChanged(t, from, to);
            } catch (RuntimeException e) {
                LOG.warning("A listener failed on " + from + " to " + to);
            }
        }
    }
    /** Scheduler only. Advances a transfer only if it has been unchanged for at least minIdle. */
    boolean advanceIfIdle(String reference, Duration minIdle) {
        Transfer t = byReference.get(normalise(reference));
        if (t == null) return false;
        synchronized (t) {
            if (t.getStatus().isTerminal()) return false;
            if (Duration.between(t.getUpdatedAt(), Instant.now()).compareTo(minIdle) < 0) return false;
            moveTo(t, t.getStatus().nextOnHappyPath().orElseThrow());
            return true;
        }
    }
}