
package Server.handlers;

import Server.security.AuthSecurityManager;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;
import java.util.Properties;
import java.io.IOException;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuthHandler {

    private final UserService userService;
    private final CartService cartService;
    private final SessionManager sessionManager;

    private static final Logger logger = LogManager.getLogger(AuthHandler.class);

    // OTP storage for password reset: email -> OTP
    private final ConcurrentHashMap<String, String> resetOTPs = new ConcurrentHashMap<>();

    // ──────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────
    public AuthHandler(UserService userService, CartService cartService, SessionManager sessionManager) {
        this.userService = userService;
        this.cartService = cartService;
        this.sessionManager = sessionManager;
    }

    public String handle(Command cmd, String[] params, Socket clientSocket) {
        switch (cmd) {
            case REGISTER:
                return handleRegister(params);
            case LOGIN:
                return handleLogin(params, clientSocket);
            case LOGOUT:
                return handleLogout(params);
            case FORGOT_PASSWORD:
                return handleForgotPassword(params);
            case RESET_PASSWORD:
                return handleResetPassword(params);
            case ADMIN_CHALLENGE: return handleAdminChallenge(params);
            default:
                return ResponseBuilder.error("Unknown auth command");
        }
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
    // REGISTER
    // ──────────────────────────────────────────────────────────────
    private String handleRegister(String[] params) {

        if (params.length < 6) {
            logger.info("[AuthHandler]: Error occurred, Missing parameters");
            return ResponseBuilder.error("Missing parameters");
        }

        javax.crypto.SecretKey key = Shared.Security.HMACUtil.currentKey.get();
        if (key == null) {
            return ResponseBuilder.error("Security context missing");
        }
        String[] registerParams = java.util.Arrays.copyOf(params, params.length - 1);
        String baseMessage = "REGISTER|" + String.join("|", registerParams);
        if (!Shared.Security.HMACUtil.verify(baseMessage, params[params.length - 1], key)) {
            return ResponseBuilder.error("Invalid message integrity");
        }

        String firstName = registerParams[0].trim();
        String lastName = registerParams[1].trim();
        String username = registerParams[2].trim();
        String password = registerParams[3];
        String email = registerParams[4].trim();

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
            logger.info("[AuthHandler] REGISTER success — user: " + username + " id: " + userId);
            return ResponseBuilder.ok(String.valueOf(userId));

        } catch (UserService.ValidationException e) {
            return ResponseBuilder.error(e.getMessage());
        } catch (UserService.DuplicateUsernameException e) {
            return ResponseBuilder.error("Username already taken");
        } catch (UserService.DuplicateEmailException e) {
            return ResponseBuilder.error("Email already registered");
        } catch (Exception e) {
            logger.error("[AuthHandler] REGISTER error: " + e.getMessage());
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

        String clientIP = clientSocket.getInetAddress().getHostAddress();
        long blockedSecs = AuthSecurityManager.getInstance().getBlockRemainingSeconds(clientIP);
        if (blockedSecs > 0) {
            long mins = blockedSecs / 60;
            long secs = blockedSecs % 60;
            logger.warn("[AuthHandler] Blocked IP attempted login: " + clientIP + " (Blocked for " + mins + "m " + secs
                    + "s)");
            return ResponseBuilder.error("Too many failed attempts. Try again in " + mins + "m " + secs + "s.");
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
            AuthSecurityManager.getInstance().handleSuccessfulLogin(clientIP);
        } catch (UserService.InvalidCredentialsException e) {
            AuthSecurityManager.getInstance().handleFailedAttempt(clientIP);
            return ResponseBuilder.error("Invalid username or password");
        } catch (Exception e) {
            logger.error("[AuthHandler] LOGIN error: " + e.getMessage());
            return ResponseBuilder.error("Server error");
        }

        String token = UUID.randomUUID().toString();

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
            logger.error("[AuthHandler] Could not load cart for user " + user.id + ": " + e.getMessage());
        }

        logger.info("[AuthHandler] LOGIN success — user: " + username
                + " | role: " + user.role
                + " | clientIP: " + clientIP
                + " | udpPort: " + udpPort);

        return ResponseBuilder.ok(token + "|" + user.role + "|" + user.email);
    }

    // ──────────────────────────────────────────────────────────────
    // LOGOUT
    // ──────────────────────────────────────────────────────────────
    private String handleLogout(String[] params) {
        if (params.length < 1) {
            return ResponseBuilder.error("Missing token");
        }
        String token = params[0];
        sessionManager.removeSession(token);
        logger.info("[AuthHandler] LOGOUT — token removed: " + token);
        return ResponseBuilder.ok();
    }

    // ──────────────────────────────────────────────────────────────
    // FORGOT PASSWORD
    // ──────────────────────────────────────────────────────────────
    private String handleForgotPassword(String[] params) {
        if (params.length < 1) {
            return ResponseBuilder.error("Missing email");
        }
        String email = params[0].trim();

        try {
            UserDTO user = userService.getUserByEmail(email);

            Random random = new Random();
            int number = 100000 + random.nextInt(900000);
            String otp = String.valueOf(number);

            resetOTPs.put(email, otp);

            // Send email
            sendMail(new InternetAddress(email), "Password Reset OTP", "Your password reset OTP is: " + otp);
            logger.info("[AuthHandler] Password reset OTP sent to " + email);

            return ResponseBuilder.ok();

        } catch (UserService.UserNotFoundException e) {
            return ResponseBuilder.error("Internal error.");
        } catch (Exception e) {
            logger.error("Error in forgot password for " + email, e);
            return ResponseBuilder.error("Internal error sending email");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // RESET PASSWORD
    // ──────────────────────────────────────────────────────────────
    private String handleResetPassword(String[] params) {
        if (params.length < 3) {
            return ResponseBuilder.error("Missing parameters");
        }
        String email = params[0].trim();
        String otp = params[1].trim();
        String newPassword = params[2];

        String storedOtp = resetOTPs.get(email);
        if (storedOtp == null || !storedOtp.equals(otp)) {
            return ResponseBuilder.error("Invalid or expired OTP");
        }

        try {
            userService.updatePassword(email, newPassword);
            resetOTPs.remove(email);
            logger.info("[AuthHandler] Password reset successful for user " + email);
            return ResponseBuilder.ok();
        } catch (Exception e) {
            logger.error("Error resetting password for " + email, e);
            return ResponseBuilder.error("Could not reset password");
        }
    }

    private void sendMail(InternetAddress recepients, String subject, String body)
            throws IOException, MessagingException {
        Properties properties = new Properties();
        Session session = Session.getDefaultInstance(properties, null);

        Message msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress("chrionline@example.com", "NoReply"));
        msg.addRecipient(Message.RecipientType.TO, recepients);
        msg.setSubject(subject);
        msg.setText(body);
        Transport.send(msg);
    }
}
