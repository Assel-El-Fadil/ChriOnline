package Server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * UDP server component — fires notification datagrams to clients.
 *
 * One instance is created by Server at startup and shared across
 * all ClientHandler threads. DatagramSocket.send() is thread-safe
 * for concurrent sends from multiple threads, so no synchronization
 * is needed here.
 *
 * Why UDP for notifications:
 *   Order confirmation notifications are one-directional and
 *   non-critical. If a packet is lost the client can still check
 *   their order history. UDP costs nothing to set up per-client
 *   (no handshake, no persistent connection) and delivers the
 *   notification in under a millisecond on a local network.
 *
 * How the server knows where to send:
 *   At LOGIN time, the client sends its UDP listen port as the
 *   last parameter: LOGIN|username|password|5002
 *   AuthHandler extracts this and stores it in SessionData alongside
 *   the client's TCP socket IP address. When OrderHandler completes
 *   a checkout it calls notify() with those two values from the session.
 *
 * Message format sent to clients:
 *   ORDER_CONFIRMED|referenceCode|totalAmount
 *   e.g.  ORDER_CONFIRMED|AB3F9C21|3748.00
 *
 * Port used:
 *   The server sends FROM a random OS-assigned source port (no bind).
 *   The client listens ON a fixed port (default 5002, sent at login).
 *   Only the destination (client) port needs to be fixed and known.
 */
public class UDPServer {

    // The single DatagramSocket used for all outgoing UDP packets.
    // Created with no port argument — the OS assigns a random source port.
    // Only receivers need a fixed port; senders do not.
    private final DatagramSocket udpSocket;

    // ────────────────────────────────────────────────────────────
    //  Constructor
    // ────────────────────────────────────────────────────────────

    /**
     * Creates and opens the UDP socket.
     * Called once by Server during startup.
     *
     * @throws RuntimeException if the OS cannot open a DatagramSocket —
     *                          this is fatal since notifications cannot work
     */
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

    /**
     * Sends a UDP datagram to the specified client.
     *
     * This method intentionally swallows all exceptions — a failed
     * notification must never affect the order that triggered it.
     * By the time this is called, the checkout transaction is already
     * committed in the database. The order exists regardless of whether
     * this packet arrives.
     *
     * DatagramSocket.send() is thread-safe for concurrent callers
     * on the same socket, so no synchronization is needed here.
     *
     * @param clientIP      the client's IP address (from SessionData)
     * @param clientUdpPort the client's UDP listen port (from SessionData)
     * @param message       the message string to send
     *                      format: ORDER_CONFIRMED|refCode|total
     */
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
            // Log but never propagate — notification failure is non-fatal
            System.err.println("[UDPBroadcaster] Failed to send to "
                    + clientIP + ":" + clientUdpPort
                    + " — " + e.getMessage());
        }
    }

    /**
     * Closes the underlying DatagramSocket.
     * Called by Server.shutdown() during graceful shutdown.
     */
    public void close() {
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
            System.out.println("[UDPBroadcaster] Socket closed.");
        }
    }
}
