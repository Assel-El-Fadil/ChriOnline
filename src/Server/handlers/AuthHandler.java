
package Server.handlers;

import Server.service.UserService;
import Shared.SessionData;
import Server.SessionManager;
import Shared.Command;
import Shared.DTO.UserDTO;
import Shared.ResponseBuilder;

import java.net.Socket;
import java.util.UUID;

public class AuthHandler {

    // ─── Dependencies injected via constructor ─────────────────────
    private final UserService     userService;
    private final SessionManager sessionManager;

    // ──────────────────────────────────────────────────────────────
    // Constructor — takes UserService and SessionManager
    // ──────────────────────────────────────────────────────────────
    public AuthHandler(UserService userService, SessionManager sessionManager) {
        this.userService     = userService;
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

        try {
            int userId = userService.register("", "", username, password, email);
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
            user = userService.authenticate(username, password);
        } catch (UserService.InvalidCredentialsException e) {
            return ResponseBuilder.error("Invalid username or password");
        } catch (Exception e) {
            System.err.println("[AuthHandler] LOGIN error: " + e.getMessage());
            return ResponseBuilder.error("Server error");
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

}
