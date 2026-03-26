package Client.network;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SocketClient {

    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private boolean connected;

    public SocketClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.connected = false;
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        connected = true;
    }

    public String sendCommand(String command) {
        if (!connected) {
            throw new NetworkException("Not connected");
        }

        writer.println(command);

        try {
            String response = reader.readLine();
            if (response == null) {
                connected = false;
                throw new NetworkException("Connection lost");
            }
            return response;
        } catch (IOException e) {
            connected = false;
            throw new NetworkException("Connection lost", e);
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
    }

    public void reconnect() {
        disconnect();

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Thread.sleep(attempt * 2000L);
                connect();
                return; // success
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
}
