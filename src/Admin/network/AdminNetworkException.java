package Admin.network;

public class AdminNetworkException extends RuntimeException {

    public AdminNetworkException(String message) { super(message); }

    public AdminNetworkException(String message, Throwable cause) {
        super(message, cause);
    }

    public AdminNetworkException(Throwable cause) {
        super(cause);
    }
}
