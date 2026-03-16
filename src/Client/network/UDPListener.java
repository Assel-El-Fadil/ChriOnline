package Client.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class UDPListener implements Runnable {
    private DatagramSocket listenSocket;
    private int listenPort;
    private NotificationCallback callback;
    private volatile boolean running = true;

    public UDPListener(int listenPort, NotificationCallback callback) throws SocketException {
        this.listenPort = listenPort;
        this.callback = callback;
        this.listenSocket = new DatagramSocket(listenPort);
    }

    @Override
    public void run() {
        byte[] buf = new byte[1024];
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);

        try {
            while (running) {
                listenSocket.receive(pkt);
                String msg = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);
                handleMessage(msg);
            }
        } catch (IOException e) {
            if (running) {
                e.printStackTrace();
            }
        } finally {
            if (listenSocket != null && !listenSocket.isClosed()) {
                listenSocket.close();
            }
        }
    }

    private void handleMessage(String msg) {
        if (callback != null) {
            callback.onNotificationReceived(msg);
        }
    }

    public void stop() {
        running = false;
        if (listenSocket != null) {
            listenSocket.close();
        }
    }
}
