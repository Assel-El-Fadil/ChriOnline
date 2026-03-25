package Server.service;

public class PaymentResult {

    private final boolean success;
    private final String  message;

    public PaymentResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String  getMessage() { return message; }

    @Override
    public String toString() {
        return "PaymentResult{success=" + success + ", message='" + message + "'}";
    }
}