package Admin.network;

import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import Shared.Security.AESUtil;

public class AdminSocket {

    private final String host;
    private final int port;
    private SSLSocket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private boolean connected;
    private SecretKey aesKey;

    public AdminSocket(String host, int port) {
        this.host = host;
        this.port = port;
        this.connected = false;
        this.aesKey = null;
    }

    public void connect() throws IOException {
        System.setProperty("javax.net.ssl.trustStore", "truststore.p12");
        System.setProperty("javax.net.ssl.trustStorePassword", "123456");
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            SSLSocketFactory sf = sslContext.getSocketFactory();
            socket = (SSLSocket) sf.createSocket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            connected = true;

            // Perform hybrid secure key exchange
            AdminSecureKeyExchange exchange = new AdminSecureKeyExchange();
            this.aesKey = exchange.performKeyExchange(reader, writer);
        } catch (Exception e) {
            disconnect();
            throw new IOException("SSL connection and secure key exchange failed: " + e.getMessage(), e);
        }
    }

    public String sendCommand(String command) {
        if (!connected) {
            throw new AdminNetworkException("Not connected");
        }

        try {
            String messageToSend;
            if (aesKey != null) {
                messageToSend = AESUtil.encrypt(command, aesKey);
            } else {
                messageToSend = command;
            }

            writer.println(messageToSend);

            String response = reader.readLine();
            if (response == null) {
                connected = false;
                throw new AdminNetworkException("Connection lost");
            }

            String decryptedResponse;
            if (aesKey != null) {
                decryptedResponse = AESUtil.decrypt(response, aesKey);
            } else {
                decryptedResponse = response;
            }

            // Transparently handle server-side session token regeneration
            if (decryptedResponse.startsWith("RENEWED_TOKEN:")) {
                int sep = decryptedResponse.indexOf("|||");
                if (sep != -1) {
                    String newToken = decryptedResponse.substring("RENEWED_TOKEN:".length(), sep);
                    String actualResponse = decryptedResponse.substring(sep + 3);
                    Admin.session.AdminAppState.updateToken(newToken);
                    return actualResponse;
                }
            }
            return decryptedResponse;
        } catch (IOException e) {
            connected = false;
            throw new AdminNetworkException("Connection lost", e);
        } catch (Exception e) {
            throw new AdminNetworkException("Cryptographic operation failed during sendCommand", e);
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
                    throw new AdminNetworkException(
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
