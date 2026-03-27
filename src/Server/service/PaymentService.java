package Server.service;

public class PaymentService {

    public PaymentResult validate(String cardNum, String holder,
                                         String expiry,  String cvv) {

        if (cardNum == null || !cardNum.matches("[0-9]{16}")) {
            return new PaymentResult(false, "Invalid card number — must be 16 digits");
        }

        if (holder == null || holder.isBlank()) {
            return new PaymentResult(false, "Invalid card holder name");
        }

        if (expiry == null || !expiry.matches("(0[1-9]|1[0-2])/[0-9]{2}")) {
            return new PaymentResult(false, "Invalid expiry date — expected format MM/YY");
        }

        if (cvv == null || !cvv.matches("[0-9]{3}")) {
            return new PaymentResult(false, "Invalid CVV — must be 3 digits");
        }

        return new PaymentResult(true, "Payment approved");
    }
}