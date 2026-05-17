package Server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Shared.*;
import Server.handlers.*;
import Server.security.SecureHandshake;
import Shared.Security.CryptoConfig;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Base64;

public class ClientHandler implements Runnable {
    private static final Logger logger = LogManager.getLogger(ClientHandler.class);


    private final Socket socket;
    private final SessionManager sessionManager;
    private final UDPServer udpServer;
    private final AuthHandler authHandler;
    private final ProductHandler productHandler;
    private final CartHandler cartHandler;
    private final OrderHandler orderHandler;
    private final AdminHandler adminHandler;
    private final UserHandler userHandler;
    private final KeyPair serverKeyPair;

    private volatile String currentToken = null;
    private volatile String pendingChallenge = null; // Used for RSA Login (Section 8)

    // ── AES session key (established via handshake) ──────────────
    private SecretKey aesSessionKey = null;
    private final SecureRandom secureRandom = new SecureRandom();

    // ────────────────────────────────────────────────────────────
    // Constructor
    // ────────────────────────────────────────────────────────────

    public ClientHandler(Socket socket,
            SessionManager sessionManager,
            UDPServer udpServer,
            AuthHandler authHandler,
            ProductHandler productHandler,
            CartHandler cartHandler,
            OrderHandler orderHandler,
            AdminHandler adminHandler,
            UserHandler userHandler,
            KeyPair serverKeyPair) {
        this.socket = socket;
        this.sessionManager = sessionManager;
        this.udpServer = udpServer;
        this.authHandler = authHandler;
        this.productHandler = productHandler;
        this.cartHandler = cartHandler;
        this.orderHandler = orderHandler;
        this.adminHandler = adminHandler;
        this.userHandler = userHandler;
        this.serverKeyPair = serverKeyPair;
    }

    // ────────────────────────────────────────────────────────────
    // Runnable entry point
    // ────────────────────────────────────────────────────────────

    @Override
    public void run() {
        String clientAddress = socket.getInetAddress().getHostAddress()
                + ":" + socket.getPort();

        BufferedReader reader = null;
        PrintWriter writer = null;

        try {
            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                    true);

            // ── Application-layer AES handshake ─────────────────────
            aesSessionKey = SecureHandshake.perform(reader, writer, serverKeyPair);
            if (aesSessionKey == null) {
                logger.error("[ClientHandler] AES handshake failed for " + clientAddress + ". Dropping connection.");
                return;
            }
            logger.info("[ClientHandler] AES session established with " + clientAddress);

            // ── Read-dispatch-respond loop ─────────────────────────
            boolean firstCommandReceived = false;
            String line;
            while ((line = reader.readLine()) != null) {

                String response;
                try {
                    // Decrypt the incoming message
                    String decryptedLine = decryptMessage(line);

                    ParsedRequest req = RequestParser.parse(decryptedLine);
                    if (!firstCommandReceived) {
                        socket.setSoTimeout(0);
                        firstCommandReceived = true;
                    }
                    Shared.Security.HMACUtil.currentKey.set(aesSessionKey);
                    try {
                        response = dispatch(req);
                    } finally {
                        Shared.Security.HMACUtil.currentKey.remove();
                    }

                } catch (RequestParser.InvalidRequestException e) {
                    response = ResponseBuilder.error("Unknown command");
                    logger.error("[ClientHandler] Bad command from "
                            + clientAddress + ": '" + line + "'");

                } catch (Exception e) {
                    response = ResponseBuilder.error("Internal server error");
                    logger.error("[ClientHandler] Unexpected error from "
                            + clientAddress + ": " + e.getMessage());
                    logger.error("Exception occurred", e);
                }

                // Encrypt the outgoing response
                String encryptedResponse = encryptMessage(response);
                writer.println(encryptedResponse);
            }

            logger.info("[ClientHandler] Client disconnected cleanly: "
                    + clientAddress);

        } catch (java.net.SocketTimeoutException e) {
            logger.warn("[ClientHandler] Dropped incomplete connection from "
                    + clientAddress + " (10s handshake timeout)");
        } catch (IOException e) {
            logger.info("[ClientHandler] Client disconnected abruptly: "
                    + clientAddress + " — " + e.getMessage());
        } finally {
            cleanup(reader, writer, clientAddress);
        }
    }

    // ────────────────────────────────────────────────────────────
    // AES-GCM Encryption / Decryption helpers
    // ────────────────────────────────────────────────────────────

    /**
     * Encrypts a plaintext message using AES-GCM.
     * Format: Base64( IV || ciphertext )
     */
    private String encryptMessage(String plaintext) {
        try {
            byte[] iv = new byte[CryptoConfig.GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CryptoConfig.AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(CryptoConfig.GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, aesSessionKey, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            logger.error("[ClientHandler] Encryption failed: " + e.getMessage(), e);
            return "ENCRYPTION_ERROR";
        }
    }

    /**
     * Decrypts an AES-GCM encrypted message.
     * Expects Base64( IV || ciphertext )
     */
    private String decryptMessage(String encryptedB64) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedB64);

            byte[] iv = new byte[CryptoConfig.GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - CryptoConfig.GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(CryptoConfig.AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(CryptoConfig.GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, aesSessionKey, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("[ClientHandler] Decryption failed: " + e.getMessage(), e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    // ────────────────────────────────────────────────────────────
    // Command dispatcher
    // ────────────────────────────────────────────────────────────

    private String dispatch(ParsedRequest req) {
        Command cmd = req.getCommand();
        String[] params = req.getParams();

        boolean renewedToken = false;
        String newSessionToken = null;

        if (currentToken != null) {
            SessionData session = sessionManager.getSession(currentToken);
            if (session != null) {
                session.updateLastActivity();
                if (session.getAgeSeconds() >= 1800) {
                    newSessionToken = sessionManager.regenerateToken(currentToken);
                    if (newSessionToken != null) {
                        currentToken = newSessionToken;
                        renewedToken = true;
                    }
                }
            } else {
                if (cmd != Command.LOGIN && cmd != Command.REGISTER && cmd != Command.LOGOUT
                        && cmd != Command.FORGOT_PASSWORD && cmd != Command.RESET_PASSWORD) {
                    currentToken = null;
                    return ResponseBuilder.error("Session expired");
                }
            }
        }

        String response = dispatchCommand(cmd, params);

        if (renewedToken && ResponseBuilder.isOk(response)) {
            response = "RENEWED_TOKEN:" + newSessionToken + "|||" + response;
        }

        return response;
    }

    private String dispatchCommand(Command cmd, String[] params) {
        switch (cmd) {

            // ── Authentication ────────────────────────────────────
            case REGISTER:
            case FORGOT_PASSWORD:
            case RESET_PASSWORD:
                return authHandler.handle(cmd, params, socket);

            case LOGIN: {
                String response = authHandler.handle(cmd, params, socket);
                if (ResponseBuilder.isOk(response)) {
                    String payload = ResponseBuilder.extractPayload(response);
                    String[] parts = payload.split("\\|", -1);
                    if (parts.length >= 1) {
                        currentToken = parts[0];
                    }
                }
                return response;
            }

            case LOGOUT: {
                String response = authHandler.handle(cmd, params, socket);
                currentToken = null;
                return response;
            }

            case ADMIN_CHALLENGE: {
                String response = authHandler.handle(cmd, params, socket);
                if (ResponseBuilder.isOk(response)) {
                    pendingChallenge = ResponseBuilder.extractPayload(response);
                }
                return response;
            }

            case ADMIN_VERIFY: {
                // Expecting params: [username, signatureB64, udpPort]
                if (params.length < 3) return ResponseBuilder.error("Missing parameters");
                if (pendingChallenge == null) return ResponseBuilder.error("No pending challenge. Request one first.");
                
                String username = params[0];
                String signature = params[1];
                int udpPort;
                try {
                    udpPort = Integer.parseInt(params[2]);
                } catch (NumberFormatException e) {
                    return ResponseBuilder.error("Invalid UDP port");
                }

                String response = authHandler.handleAdminVerify(username, signature, pendingChallenge, udpPort, socket);
                
                if (ResponseBuilder.isOk(response)) {
                    pendingChallenge = null; // Clear challenge after success
                    String payload = ResponseBuilder.extractPayload(response);
                    String[] parts = payload.split("\\|", -1);
                    if (parts.length >= 1) {
                        currentToken = parts[0];
                    }
                }
                return response;
            }

            // ── Product browsing ──────────────────────────────────
            case GET_PRODUCTS:
                return productHandler.handle(cmd, params, currentToken);

            case GET_PRODUCT:
                return productHandler.handle(cmd, params, currentToken);

            case GET_CATEGORIES:
                return productHandler.handle(cmd, params, currentToken);

            // ── Cart ──────────────────────────────────────────────
            case CART_ADD:
                return cartHandler.handleAdd(params);

            case CART_REMOVE:
                return cartHandler.handleRemove(params);

            case CART_VIEW:
                return cartHandler.handleView(params);

            case CART_CLEAR:
                return cartHandler.handleClear(params);

            // ── Orders ────────────────────────────────────────────
            case CHECKOUT:
                return orderHandler.handle(cmd, params);

            case CHECKOUT_INIT:
                return orderHandler.handle(cmd, params);

            case CHECKOUT_CONFIRM:
                return orderHandler.handle(cmd, params);

            case ORDER_HISTORY:
                return orderHandler.handle(cmd, params);

            case GET_ORDER_STATUS:
                return orderHandler.handle(cmd, params);

            case GET_ORDER_ITEMS:
                return orderHandler.handle(cmd, params);

            // ── Admin ─────────────────────────────────────────────
            case ADMIN_ADD_PRODUCT:
                return adminHandler.handleAddProduct(params);

            case ADMIN_EDIT_PRODUCT:
                return adminHandler.handleEditProduct(params);

            case ADMIN_DELETE_PRODUCT:
                return adminHandler.handleDeleteProduct(params);

            case ADMIN_LIST_ORDERS:
                return adminHandler.handleListOrders(params);

            case ADMIN_UPDATE_STATUS:
                return adminHandler.handleUpdateStatus(params);

            case ADMIN_LIST_USERS:
                return adminHandler.handleListUsers(params);

            case ADMIN_DELETE_USER:
            case ADMIN_HARD_DELETE_USER:
                return adminHandler.handleHardDeleteUser(params);

            case ADMIN_DEACTIVATE_USER:
                return adminHandler.handleDeactivateUser(params);

            case ADMIN_ACTIVATE_USER:
                return adminHandler.handleActivateUser(params);

            // ── User Profile ───────────────────────────────────────
            case GET_PROFILE:
                return userHandler.handleGetProfile(params);

            case EDIT_PROFILE:
                return userHandler.handleEditProfile(params);

            default:
                return ResponseBuilder.error("Command not implemented: " + cmd);
        }
    }

    // ────────────────────────────────────────────────────────────
    // Cleanup
    // ────────────────────────────────────────────────────────────

    private void cleanup(BufferedReader reader,
            PrintWriter writer,
            String clientAddress) {

        if (currentToken != null) {
            sessionManager.removeSession(currentToken);
            currentToken = null;
        }

        if (writer != null) {
            writer.close();
        }

        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
            }
        }

        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }

        logger.info("[ClientHandler] Resources released for: " + clientAddress);
    }
}