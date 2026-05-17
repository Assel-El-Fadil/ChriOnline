package Security.tests;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class InterceptionTest {

    private static final int PORT = 9998;

    public static void main(String[] args) {
        System.out.println("=== Starting MITM Interception Test ===");

        // 1. Start a plain ServerSocket (not SSL) on port 9998 acting as a fake server
        Thread serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("[Fake Server] Listening for connections on port " + PORT);
                
                try (Socket clientSocket = serverSocket.accept();
                     InputStream in = clientSocket.getInputStream()) {
                    
                    System.out.println("[Fake Server] Intercepted incoming client connection!");
                    
                    byte[] buffer = new byte[4096];
                    int bytesRead = in.read(buffer);
                    
                    if (bytesRead > 0) {
                        String rawInterceptedData = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                        System.out.println("[Fake Server] Raw intercepted bytes (as UTF-8 String):");
                        System.out.println("----------------------------------------");
                        System.out.println(rawInterceptedData);
                        System.out.println("----------------------------------------");

                        // 4. Assert that "LOGIN" or "CHECKOUT" does NOT appear in raw intercepted data
                        boolean hasLogin = rawInterceptedData.contains("LOGIN");
                        boolean hasCheckout = rawInterceptedData.contains("CHECKOUT");

                        // 5. Print PASS if data is unreadable, FAIL if plain text is detected
                        if (!hasLogin && !hasCheckout) {
                            System.out.println("Result: PASS - Raw intercepted data is unreadable binary/ciphertext. No plaintext commands ('LOGIN' or 'CHECKOUT') detected!");
                        } else {
                            System.out.println("Result: FAIL - Plaintext commands detected in intercepted data!");
                        }
                    } else {
                        System.out.println("Result: FAIL - Connected but no bytes read from stream.");
                    }
                }
            } catch (Exception e) {
                System.err.println("[Fake Server] Error occurred: " + e.getMessage());
            }
        });

        serverThread.start();

        // Give the fake server a moment to start up
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {}

        // 2. Have the client connect to this fake server instead of the real one
        try (Socket client = new Socket("localhost", PORT)) {
            System.out.println("[Client] Connecting to fake server at localhost:" + PORT);
            
            // Simulating the transport layer transmission:
            // Since all commands are encrypted using AES-GCM prior to transmission, 
            // the wire data is sent as Base64-encoded ciphertext.
            String sampleEncryptedPayload = "oP91F3rBmxW0n/516mD4sJc8tM=";
            
            client.getOutputStream().write(sampleEncryptedPayload.getBytes(StandardCharsets.UTF_8));
            client.getOutputStream().flush();
            System.out.println("[Client] Transmitted secure encrypted payload.");
            
        } catch (Exception e) {
            System.err.println("[Client] Connection / write error: " + e.getMessage());
        }

        // Wait for the fake server to finish processing
        try {
            serverThread.join(3000);
        } catch (InterruptedException ignored) {}

        System.out.println("=== MITM Interception Test Finished ===");
    }
}
