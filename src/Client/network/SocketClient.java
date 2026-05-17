package Client.network;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import Shared.Security.AESUtil;

public class SocketClient {

    private final String host;
    private final int port;
    private SSLSocket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private boolean connected;
    private SecretKey aesKey;

    public SocketClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.connected = false;
        this.aesKey = null;
    }

    public void connect() throws IOException {
        System.setProperty("javax.net.ssl.trustStore", "truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "123456");

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            SSLSocketFactory sf = sslContext.getSocketFactory();
            socket = (SSLSocket) sf.createSocket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            connected = true;

            // Perform hybrid secure key exchange
            SecureKeyExchange exchange = new SecureKeyExchange();
            this.aesKey = exchange.performKeyExchange(reader, writer);
        } catch (Exception e) {
            disconnect();
            throw new IOException("SSL connection and secure key exchange failed: " + e.getMessage(), e);
        }
    }

    public String sendCommand(String command) {
        if (!connected) {
            throw new NetworkException("Not connected");
        }

        try {
            String messageToSend;
            if (aesKey != null) {
                byte[] encryptedBytes = AESUtil.encrypt(command.getBytes(StandardCharsets.UTF_8), aesKey);
                messageToSend = Base64.getEncoder().encodeToString(encryptedBytes);
            } else {
                messageToSend = command;
            }

            writer.println(messageToSend);

            String response = reader.readLine();
            if (response == null) {
                connected = false;
                throw new NetworkException("Connection lost");
            }

            String decryptedResponse;
            if (aesKey != null) {
                byte[] decodedBytes = Base64.getDecoder().decode(response);
                byte[] decryptedBytes = AESUtil.decrypt(decodedBytes, aesKey);
                decryptedResponse = new String(decryptedBytes, StandardCharsets.UTF_8);
            } else {
                decryptedResponse = response;
            }

            // Transparently handle server-side session token regeneration
            if (decryptedResponse.startsWith("RENEWED_TOKEN:")) {
                int sep = decryptedResponse.indexOf("|||");
                if (sep != -1) {
                    String newToken = decryptedResponse.substring("RENEWED_TOKEN:".length(), sep);
                    String actualResponse = decryptedResponse.substring(sep + 3);
                    Client.session.AppState.updateToken(newToken);
                    return actualResponse;
                }
            }
            return decryptedResponse;
        } catch (IOException e) {
            connected = false;
            throw new NetworkException("Connection lost", e);
        } catch (Exception e) {
            throw new NetworkException("Cryptographic operation failed during sendCommand", e);
        }
    }

    public void disconnect() {
        try {
            if (writer != null)
                writer.close();
            if (reader != null)
                reader.close();
            if (socket != null)
                socket.close();
        } catch (IOException ignored) {
            // silently ignore
        }
        connected = false;
        aesKey = null;
    }

    public void reconnect() {
        disconnect();

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Thread.sleep(attempt * 2000L);
                connect();
                return;
            } catch (IOException | InterruptedException e) {
                if (attempt == maxRetries) {
                    throw new NetworkException(
                            "Failed to reconnect after " + maxRetries + " attempts");
                }
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public SecretKey getAesKey() {
        return aesKey;
    }
}
