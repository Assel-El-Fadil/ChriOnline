
package Server.handlers;

import Server.service.CartService;
import Server.service.UserService;
import Shared.SessionData;
import Server.SessionManager;
import Shared.Command;
import Shared.DTO.UserDTO;
import Shared.ResponseBuilder;
import Shared.Security.ChallengeGenerator;
import Shared.Security.RSAKeyPairGenerator;
import Shared.Security.Verifier;

import java.net.Socket;
import java.security.PublicKey;
import java.util.Base64;
import java.util.UUID;

public class AuthHandler {

    private final UserService     userService;
    private final CartService     cartService;
    private final SessionManager sessionManager;

    // ──────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────
    public AuthHandler(UserService userService, CartService cartService, SessionManager sessionManager) {
        this.userService     = userService;
        this.cartService     = cartService;
        this.sessionManager = sessionManager;
    }

    public String handle(Command cmd, String[] params, Socket clientSocket) {
        switch (cmd) {
            case REGISTER:        return handleRegister(params);
            case LOGIN:           return handleLogin(params, clientSocket);
            case LOGOUT:          return handleLogout(params);
            case ADMIN_CHALLENGE: return handleAdminChallenge(params);
            default:              return ResponseBuilder.error("Unknown auth command");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // REGISTER
    // ──────────────────────────────────────────────────────────────
    private String handleRegister(String[] params) {

        if (params.length < 5) {
            return ResponseBuilder.error("Missing parameters");
        }

        String firstName = params[0].trim();
        String lastName  = params[1].trim();
        String username  = params[2].trim();
        String password  = params[3];
        String email     = params[4].trim();

        if (firstName.isEmpty()) {
            return ResponseBuilder.error("First name cannot be empty");
        }
        if (lastName.isEmpty()) {
            return ResponseBuilder.error("Last name cannot be empty");
        }
        if (username.isEmpty()) {
            return ResponseBuilder.error("Username cannot be empty");
        }
        if (password.length() < 6) {
            return ResponseBuilder.error("Password must be at least 6 characters");
        }
        if (!email.contains("@")) {
            return ResponseBuilder.error("Invalid email address");
        }

        try {
            int userId = userService.register(firstName, lastName, username, password, email);
            System.out.println("[AuthHandler] REGISTER success — user: " + username + " id: " + userId);
            return ResponseBuilder.ok(String.valueOf(userId));

        } catch (UserService.ValidationException e) {
            return ResponseBuilder.error(e.getMessage());
        } catch (UserService.DuplicateUsernameException e) {
            return ResponseBuilder.error("Username already taken");
        } catch (UserService.DuplicateEmailException e) {
            return ResponseBuilder.error("Email already registered");
        } catch (Exception e) {
            System.err.println("[AuthHandler] REGISTER error: " + e.getMessage());
            return ResponseBuilder.error("Registration failed");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // LOGIN
    // ──────────────────────────────────────────────────────────────
    private String handleLogin(String[] params, Socket clientSocket) {

        if (params.length < 3) {
            return ResponseBuilder.error("Missing parameters");
        }

        String username = params[0].trim();
        String password = params[1];
        int udpPort;

        try {
            udpPort = Integer.parseInt(params[2].trim());
        } catch (NumberFormatException e) {
            return ResponseBuilder.error("Invalid UDP port");
        }

        UserDTO user;
        try {
            user = userService.authenticate(username, password);
        } catch (UserService.InvalidCredentialsException e) {
            return ResponseBuilder.error("Invalid username or password");
        } catch (Exception e) {
            System.err.println("[AuthHandler] LOGIN error: " + e.getMessage());
            return ResponseBuilder.error("Server error");
        }

        String token = UUID.randomUUID().toString();
        String clientIP = clientSocket.getInetAddress().getHostAddress();

        SessionData sessionData = new SessionData(
                token,
                user.id,
                user.role,
                user.username,
                clientIP,
                udpPort
        );
        sessionManager.addSession(token, sessionData);

        try {
            cartService.loadFromDB(token, user.id);
        } catch (Exception e) {
            System.err.println("[AuthHandler] Could not load cart for user " + user.id + ": " + e.getMessage());
        }

        System.out.println("[AuthHandler] LOGIN success — user: " + username
                + " | role: " + user.role
                + " | clientIP: " + clientIP
                + " | udpPort: " + udpPort);

        return ResponseBuilder.ok(token + "|" + user.role);
    }

    // ──────────────────────────────────────────────────────────────
    // ADMIN RSA AUTH (Section 8)
    // ──────────────────────────────────────────────────────────────

    private String handleAdminChallenge(String[] params) {
        if (params.length < 1) return ResponseBuilder.error("Missing username");
        String username = params[0].trim();

        // Check if user exists and is admin
        var authUser = userService.findAuthUserByUsername(username);
        if (authUser == null || !"ADMIN".equals(authUser.role)) {
            return ResponseBuilder.error("Admin access denied");
        }

        return ResponseBuilder.ok(ChallengeGenerator.generateChallenge());
    }

    public String handleAdminVerify(String username, String signatureB64, String challenge, int udpPort, Socket clientSocket) {
        // 1. Fetch user (must be admin)
        var authUser = userService.findAuthUserByUsername(username); 
        if (authUser == null || !"ADMIN".equals(authUser.role)) {
            return ResponseBuilder.error("Admin access denied");
        }

        if (authUser.publicKey == null || authUser.publicKey.isEmpty()) {
            return ResponseBuilder.error("No public key registered for this admin");
        }

        // 2. Verify Signature
        try {
            byte[] signatureBytes = Base64.getDecoder().decode(signatureB64);
            PublicKey publicKey = RSAKeyPairGenerator.loadPublicKeyFromString(authUser.publicKey);
            
            boolean isValid = Verifier.verify(challenge, signatureBytes, publicKey);
            if (!isValid) {
                return ResponseBuilder.error("Invalid signature");
            }

            // 3. Success -> Create Session (cloned from handleLogin)
            String token = UUID.randomUUID().toString();
            String clientIP = clientSocket.getInetAddress().getHostAddress();

            SessionData sessionData = new SessionData(
                    token, authUser.id, authUser.role, authUser.username, clientIP, udpPort
            );
            sessionManager.addSession(token, sessionData);
            cartService.loadFromDB(token, authUser.id);

            System.out.println("[AuthHandler] ADMIN RSA LOGIN success — user: " + username);
            return ResponseBuilder.ok(token + "|" + authUser.role);

        } catch (Exception e) {
            System.err.println("[AuthHandler] RSA Verification error: " + e.getMessage());
            return ResponseBuilder.error("Verification failed");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // LOGOUT
    // ──────────────────────────────────────────────────────────────
    private String handleLogout(String[] params) {
        if (params.length < 1) {
            return ResponseBuilder.error("Missing token");
        }
        sessionManager.removeSession(params[0]);
        System.out.println("[AuthHandler] LOGOUT — token removed: " + params[0]);
        return ResponseBuilder.ok();
    }
}
