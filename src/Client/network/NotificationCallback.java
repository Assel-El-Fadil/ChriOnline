package Client.network;

@FunctionalInterface
public interface NotificationCallback {
    void onOrderConfirmed(String refCode, String total);
}
