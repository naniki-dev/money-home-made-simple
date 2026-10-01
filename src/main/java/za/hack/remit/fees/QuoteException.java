package za.hack.remit.fees;

public class QuoteException extends RuntimeException{
    public static final String MIN_AMOUNT = "error.min_amount";
    public static final String MAX_AMOUNT = "error.max_amount";
    public static final String INVALID_AMOUNT = "error.invalid_amount";
    public static final String RATE_EXPIRED = "error.rate_expired";

    private final String messageKey;

    public QuoteException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
