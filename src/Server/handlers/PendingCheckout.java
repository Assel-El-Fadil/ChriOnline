package Server.handlers;

public class PendingCheckout {
    public final String paymentMethod;
    public final String cardNum;
    public final String holder;
    public final String expiry;
    public final String cvv;
    public final String verificationCode;
    public final String transactionId;
    public final long timestamp;

    public PendingCheckout(String paymentMethod, String cardNum, String holder, String expiry, String cvv, String verificationCode, String transactionId) {
        this.paymentMethod = paymentMethod;
        this.cardNum = cardNum;
        this.holder = holder;
        this.expiry = expiry;
        this.cvv = cvv;
        this.verificationCode = verificationCode;
        this.transactionId = transactionId;
        this.timestamp = System.currentTimeMillis();
    }
}
