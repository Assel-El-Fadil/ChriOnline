package chri.service;

import chri.dao.UserDAO;
import chri.dao.UserDAO.AuthUser;
import chri.shared.UserDTO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.regex.Pattern;

public class UserService {

    private static final int    USERNAME_MIN   = 3;
    private static final int    USERNAME_MAX   = 50;
    private static final int    PASSWORD_MIN   = 6;
    private static final int    EMAIL_MAX      = 150;
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserDAO userDAO;

    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public int register(String username, String password, String email) {

        validateUsername(username);
        validatePassword(password);
        validateEmail(email);

        String passwordHash = hashPassword(password);

        try {
            return userDAO.createUser(username, passwordHash, email);

        } catch (UserDAO.DuplicateUsernameException e) {
            throw new DuplicateUsernameException(e.getMessage());
        } catch (UserDAO.DuplicateEmailException e) {
            throw new DuplicateEmailException(e.getMessage());
        }
    }

    public UserDTO authenticate(String username, String password) {
        AuthUser authUser = userDAO.findByUsernameForAuth(username);

        if (authUser == null) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        String incomingHash = hashPassword(password);

        if (!incomingHash.equals(authUser.passwordHash)) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        return new UserDTO(
                authUser.id,
                authUser.username,
                authUser.email,
                authUser.role,
                authUser.active
        );
    }

    public UserDTO findById(int userId) {
        UserDTO user = userDAO.findById(userId);
        if (user == null) {
            throw new UserNotFoundException("No user found with id=" + userId);
        }
        return user;
    }

    public List<UserDTO> findAll() {
        return userDAO.findAll();
    }

    public void delete(int userId) {
        UserDTO user = userDAO.findById(userId);
        if (user == null) {
            throw new UserNotFoundException("No user found with id=" + userId);
        }

        if (userDAO.hasOrders(userId)) {
            userDAO.softDelete(userId);
        } else {
            userDAO.hardDelete(userId);
        }
    }

    public String hashPassword(String plainTextPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(
                    plainTextPassword.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("Username cannot be empty");
        }
        if (username.length() < USERNAME_MIN) {
            throw new ValidationException(
                    "Username must be at least " + USERNAME_MIN + " characters");
        }
        if (username.length() > USERNAME_MAX) {
            throw new ValidationException(
                    "Username cannot exceed " + USERNAME_MAX + " characters");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new ValidationException(
                    "Username can only contain letters, numbers, and underscores");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new ValidationException("Password cannot be empty");
        }
        if (password.length() < PASSWORD_MIN) {
            throw new ValidationException(
                    "Password must be at least " + PASSWORD_MIN + " characters");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ValidationException("Email cannot be empty");
        }
        if (email.length() > EMAIL_MAX) {
            throw new ValidationException(
                    "Email cannot exceed " + EMAIL_MAX + " characters");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException(
                    "Email format is invalid — expected format: user@domain.com");
        }
    }

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) { super(message); }
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) { super(message); }
    }

    public static class DuplicateUsernameException extends RuntimeException {
        public DuplicateUsernameException(String message) { super(message); }
    }

    public static class DuplicateEmailException extends RuntimeException {
        public DuplicateEmailException(String message) { super(message); }
    }
}