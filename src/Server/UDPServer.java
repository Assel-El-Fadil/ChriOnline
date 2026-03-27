package Server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class UDPServer {

    private final DatagramSocket udpSocket;

    // ────────────────────────────────────────────────────────────
    //  Constructor
    // ────────────────────────────────────────────────────────────

    public UDPServer() {
        try {
            this.udpSocket = new DatagramSocket();
            System.out.println("[UDPBroadcaster] Ready — source port: "
                    + udpSocket.getLocalPort());
        } catch (SocketException e) {
            throw new RuntimeException(
                    "[UDPBroadcaster] FATAL: Could not open UDP socket — "
                            + e.getMessage(), e);
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Public API
    // ────────────────────────────────────────────────────────────

    public void notify(String clientIP, int clientUdpPort, String message) {
        if (clientIP == null || clientIP.isBlank() || clientUdpPort <= 0) {
            System.err.println("[UDPBroadcaster] Invalid destination — "
                    + "ip='" + clientIP + "' port=" + clientUdpPort);
            return;
        }

        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            InetAddress addr = InetAddress.getByName(clientIP);
            DatagramPacket packet = new DatagramPacket(
                    data, data.length, addr, clientUdpPort);

            udpSocket.send(packet);

            System.out.println("[UDPBroadcaster] Sent to " + clientIP + ":" + clientUdpPort
                    + " — " + message);

        } catch (IOException e) {

            System.err.println("[UDPBroadcaster] Failed to send to "
                    + clientIP + ":" + clientUdpPort
                    + " — " + e.getMessage());
        }
    }

    public void close() {
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
            System.out.println("[UDPBroadcaster] Socket closed.");
        }
    }
}
