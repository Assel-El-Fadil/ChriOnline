
package Server.handlers;

import Server.DAO.UserDAO;
import Server.SessionData;
import Server.SessionManager;
import Shared.Command;
import Shared.DTO.UserDTO;
import Shared.ResponseBuilder;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class AuthHandler {

    // ─── Dependencies injected via constructor ─────────────────────
    private final UserDAO        userDAO;
    private final SessionManager sessionManager;

    // ──────────────────────────────────────────────────────────────
    // Constructor — takes UserDAO and SessionManager
    // ──────────────────────────────────────────────────────────────
    public AuthHandler(UserDAO userDAO, SessionManager sessionManager) {
        this.userDAO        = userDAO;
        this.sessionManager = sessionManager;
    }

    // ──────────────────────────────────────────────────────────────
    // Main entry point — called by ClientHandler dispatch switch
    // ──────────────────────────────────────────────────────────────
    public String handle(Command cmd, String[] params, Socket clientSocket) {
        switch (cmd) {
            case REGISTER: return handleRegister(params);
            case LOGIN:    return handleLogin(params, clientSocket);
            case LOGOUT:   return handleLogout(params);
            default:       return ResponseBuilder.error("Unknown auth command");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // REGISTER
    // params: 0=username, 1=password, 2=email
    // returns: OK|userId  or  ERR|message
    // ──────────────────────────────────────────────────────────────
    private String handleRegister(String[] params) {

        if (params.length < 3) {
            return ResponseBuilder.error("Missing parameters");
        }

        String username = params[0].trim();
        String password = params[1];
        String email    = params[2].trim();

        if (username.isEmpty()) {
            return ResponseBuilder.error("Username cannot be empty");
        }
        if (password.length() < 6) {
            return ResponseBuilder.error("Password must be at least 6 characters");
        }
        if (!email.contains("@")) {
            return ResponseBuilder.error("Invalid email address");
        }

        String passwordHash = hashPassword(password);
        if (passwordHash == null) {
            return ResponseBuilder.error("Server error during registration");
        }

        try {
            int userId = userDAO.createUser(username, passwordHash, email);
            System.out.println("[AuthHandler] REGISTER success — user: " + username + " id: " + userId);
            return ResponseBuilder.ok(String.valueOf(userId));

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("duplicate") || msg.contains("unique")) {
                return ResponseBuilder.error("Username already taken");
            }
            System.err.println("[AuthHandler] REGISTER error: " + e.getMessage());
            return ResponseBuilder.error("Registration failed");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // LOGIN
    // params: 0=username, 1=password, 2=udpPort
    // returns: OK|token|role  or  ERR|message
    // ──────────────────────────────────────────────────────────────
    private String handleLogin(String[] params, Socket clientSocket) {

        if (params.length < 3) {
            return ResponseBuilder.error("Missing parameters");
        }

        String username = params[0].trim();
        String password = params[1];
        int    udpPort;

        try {
            udpPort = Integer.parseInt(params[2].trim());
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("Invalid UDP port");
        }

        UserDTO user;
        try {
            user = userDAO.findByUsername(username);
        } catch (Exception e) {
            System.err.println("[AuthHandler] LOGIN DB error: " + e.getMessage());
            return ResponseBuilder.error("Server error");
        }

        if (user == null) {
            return ResponseBuilder.error("Invalid username or password");
        }

        String inputHash = hashPassword(password);
        String storedHash;
        try {
            storedHash = userDAO.getPasswordHash(username);
        } catch (Exception e) {
            System.err.println("[AuthHandler] LOGIN hash fetch error: " + e.getMessage());
            return ResponseBuilder.error("Server error");
        }

        if (inputHash == null || !inputHash.equals(storedHash)) {
            return ResponseBuilder.error("Invalid username or password");
        }

        String token    = UUID.randomUUID().toString();
        String clientIP = clientSocket.getInetAddress().getHostAddress();

        SessionData sessionData = new SessionData(
                token,
                user.id,
                user.role,
                clientIP,
                udpPort
        );
        sessionManager.addSession(token, sessionData);

        System.out.println("[AuthHandler] LOGIN success — user: " + username
                + " | role: " + user.role
                + " | clientIP: " + clientIP
                + " | udpPort: " + udpPort);

        return ResponseBuilder.ok(token + "|" + user.role);
    }

    // ──────────────────────────────────────────────────────────────
    // LOGOUT
    // params: 0=token
    // returns: OK|
    // ──────────────────────────────────────────────────────────────
    private String handleLogout(String[] params) {
        if (params.length < 1) {
            return ResponseBuilder.error("Missing token");
        }
        sessionManager.removeSession(params[0]);
        System.out.println("[AuthHandler] LOGOUT — token removed: " + params[0]);
        return ResponseBuilder.ok();
    }

    // ──────────────────────────────────────────────────────────────
    // SHA-256 — converts password → 64-char hex string (one-way)
    // ──────────────────────────────────────────────────────────────
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("[AuthHandler] SHA-256 not available: " + e.getMessage());
            return null;
        }
    }
}
