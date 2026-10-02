package za.hack.remit.transfer;

import java.math.BigDecimal;
import java.time.Instant;

public class Transfer {
    private final String reference;
    private final String sessionId;
    private final String senderPhone;
    private final String recipientName;
    private final String recipientPhone;
    private final String language;
    private final BigDecimal amountZar;
    private final BigDecimal feeZar;
    private final BigDecimal totalZar;
    private final BigDecimal rate;              // rand per 1 US dollar
    private final BigDecimal receiveAmountUsd;
    private final Instant createdAt;

    private volatile TransferStatus status;
    private volatile Instant updatedAt;

    public Transfer(String reference, String sessionId, String senderPhone,
                    String recipientName, String recipientPhone, String language,
                    BigDecimal amountZar, BigDecimal feeZar, BigDecimal totalZar,
                    BigDecimal rate, BigDecimal receiveAmountUsd) {
        this.reference = reference;
        this.sessionId = sessionId;
        this.senderPhone = senderPhone;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.language = language;
        this.amountZar = amountZar;
        this.feeZar = feeZar;
        this.totalZar = totalZar;
        this.rate = rate;
        this.receiveAmountUsd = receiveAmountUsd;
        this.status = TransferStatus.SENT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getReference() { return reference; }
    public String getSessionId() { return sessionId; }
    public TransferStatus getStatus() { return status; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getLanguage() { return language; }
    public BigDecimal getAmountZar() { return amountZar; }
    public BigDecimal getFeeZar() { return feeZar; }
    public BigDecimal getTotalZar() { return totalZar; }
    public BigDecimal getRate() { return rate; }
    public BigDecimal getReceiveAmountUsd() { return receiveAmountUsd; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getSenderPhone() { return senderPhone; }
    // No "public": only TransferService, in this same package, can change the status.
    void setStatus(TransferStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }
    public boolean isOwnedBy(String phone) {
        return senderPhone.equals(phone);
    }
}