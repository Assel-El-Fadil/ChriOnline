package Server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class UDPServer {
    private static final Logger logger = LogManager.getLogger(UDPServer.class);

    private static final int MAX_PACKETS_PER_SECOND = 100;

    // IP -> [windowStartMs, packetCount]
    private final ConcurrentHashMap<String, long[]> udpRateLimiter = new ConcurrentHashMap<>();

    private final DatagramSocket udpSocket;

    // ────────────────────────────────────────────────────────────
    //  Constructor
    // ────────────────────────────────────────────────────────────

    public UDPServer() {
        try {
            this.udpSocket = new DatagramSocket();
            logger.info("[UDPBroadcaster] Ready — source port: "
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
            logger.error("[UDPBroadcaster] Invalid destination — "
                    + "ip='" + clientIP + "' port=" + clientUdpPort);
            return;
        }

        // UDP Flood Protection: Rate-limit outbound packets per destination IP
        if (isUDPRateLimited(clientIP)) {
            logger.warn("[UDPBroadcaster] UDP Flood detected from " + clientIP + ". Dropping packet.");
            return;
        }

        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            InetAddress addr = InetAddress.getByName(clientIP);
            DatagramPacket packet = new DatagramPacket(
                    data, data.length, addr, clientUdpPort);

            udpSocket.send(packet);

            logger.info("[UDPBroadcaster] Sent to " + clientIP + ":" + clientUdpPort
                    + " — " + message);

        } catch (IOException e) {

            logger.error("[UDPBroadcaster] Failed to send to "
                    + clientIP + ":" + clientUdpPort
                    + " — " + e.getMessage());
        }
    }

    private boolean isUDPRateLimited(String ip) {
        long now = System.currentTimeMillis();
        long windowMs = 1000L; // 1 second window

        udpRateLimiter.compute(ip, (k, v) -> {
            if (v == null || now - v[0] > windowMs) {
                return new long[]{now, 1};
            }
            v[1]++;
            return v;
        });

        long[] data = udpRateLimiter.get(ip);
        return data != null && data[1] > MAX_PACKETS_PER_SECOND;
    }

    public void close() {
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
            logger.info("[UDPBroadcaster] Socket closed.");
        }
    }
}
