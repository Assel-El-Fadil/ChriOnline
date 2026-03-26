package Server.service;

public class PaymentService {

    // ──────────────────────────────────────────────────────────────
    // validate() — simulated card validation, no real banking
    //
    // params match the CHECKOUT command:
    //   CHECKOUT|token|CARD|cardNum|holder|expiry|cvv
    //                        [0]     [1]    [2]   [3]
    // ──────────────────────────────────────────────────────────────
    public PaymentResult validate(String cardNum, String holder,
                                         String expiry,  String cvv) {

        // 1. Card number — must be exactly 16 digits
        if (cardNum == null || !cardNum.matches("[0-9]{16}")) {
            return new PaymentResult(false, "Invalid card number — must be 16 digits");
        }

        // 2. Holder name — must not be blank
        if (holder == null || holder.isBlank()) {
            return new PaymentResult(false, "Invalid card holder name");
        }

        // 3. Expiry — must match MM/YY format (month 01–12)
        if (expiry == null || !expiry.matches("(0[1-9]|1[0-2])/[0-9]{2}")) {
            return new PaymentResult(false, "Invalid expiry date — expected format MM/YY");
        }

        // 4. CVV — must be exactly 3 digits
        if (cvv == null || !cvv.matches("[0-9]{3}")) {
            return new PaymentResult(false, "Invalid CVV — must be 3 digits");
        }

        // All checks passed
        return new PaymentResult(true, "Payment approved");
    }
}