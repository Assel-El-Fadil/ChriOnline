package Server.service;

import Server.DAO.UserDAO;
import Server.DAO.UserDAO.AuthUser;
import Shared.DTO.UserDTO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.regex.Pattern;

public class UserService {

    private static final int     USERNAME_MIN     = 3;
    private static final int     USERNAME_MAX     = 50;
    private static final int     PASSWORD_MIN     = 6;
    private static final int     NAME_MAX         = 50;
    private static final int     EMAIL_MAX        = 150;
    private static final int     ADDRESS_MAX      = 255;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L} '\\-]+$");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserDAO userDAO;

    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }


    public int register(String firstName, String lastName,
                        String username, String password, String email) {

        validateFirstName(firstName);
        validateLastName(lastName);
        validateUsername(username);
        validatePassword(password);
        validateEmail(email);

        String passwordHash = hashPassword(password);

        try {
            // address is null at registration — user sets it later
            return userDAO.createUser(
                    firstName, lastName, username, passwordHash, email, null);

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

        if (!hashPassword(password).equals(authUser.passwordHash)) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        // Build UserDTO — all new fields included, no hash exposed
        return new UserDTO(
                authUser.id,
                authUser.username,
                authUser.firstName,
                authUser.lastName,
                authUser.email,
                authUser.address,
                authUser.profilePhoto,
                authUser.role,
                authUser.active
        );
    }


    public UserDTO getProfile(int userId) {
        UserDTO user = userDAO.findById(userId);
        if (user == null) {
            throw new UserNotFoundException("No user found with id=" + userId);
        }
        return user;
    }

    // ────────────────────────────────────────────────────────────
    //  Profile update
    // ────────────────────────────────────────────────────────────

    /**
     * Maps user-facing field names (as sent by the client protocol)
     * to the actual database column names, validates the new value,
     * then delegates to UserDAO.updateProfile().
     */
    public void updateProfile(int userId, String field, String value) {
        // Map client field name → DB column name
        String column = switch (field) {
            case "firstName"    -> "first_name";
            case "lastName"     -> "last_name";
            case "email"        -> "email";
            case "address"      -> "address";
            case "profilePhoto" -> "profile_photo";
            default -> throw new ValidationException(
                    "Unknown editable field: " + field);
        };

        // Validate the new value using existing validators where applicable
        switch (field) {
            case "firstName"    -> validateFirstName(value);
            case "lastName"     -> validateLastName(value);
            case "email"        -> validateEmail(value);
            case "address"      -> validateAddress(value);
            // profilePhoto — no validation beyond length, just pass through
        }

        try {
            boolean updated = userDAO.updateProfile(userId, column, value);
            if (!updated) {
                throw new UserNotFoundException(
                        "No active user found with id=" + userId);
            }
        } catch (UserDAO.DuplicateEmailException e) {
            throw new DuplicateEmailException(e.getMessage());
        }
    }

    public List<UserDTO> findAll() {
        return userDAO.findAll();
    }

    // ────────────────────────────────────────────────────────────
    //  Deletion
    // ────────────────────────────────────────────────────────────

    public void deactivate(int userId) {
        UserDTO user = userDAO.findById(userId);
        if (user == null) {
            throw new UserNotFoundException("No user found with id=" + userId);
        }
        userDAO.softDelete(userId);
    }

    public void activate(int userId) {
        UserDTO user = userDAO.findById(userId);
        if (user == null) {
            throw new UserNotFoundException("No user found with id=" + userId);
        }
        userDAO.reactivate(userId);
    }

    public void hardDelete(int userId) {
        UserDTO user = userDAO.findById(userId);
        if (user == null) {
            throw new UserNotFoundException("No user found with id=" + userId);
        }
        if (userDAO.hasOrders(userId)) {
            throw new IllegalStateException("Cannot hard delete a user with orders. Deactivate instead.");
        }
        userDAO.hardDelete(userId);
    }

    // ────────────────────────────────────────────────────────────
    //  Password hashing
    // ────────────────────────────────────────────────────────────

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
            // SHA-256 is guaranteed in every JVM — never happens
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Validation helpers
    // ────────────────────────────────────────────────────────────

    private void validateFirstName(String firstName) {
        if (firstName == null || firstName.isBlank()) {
            throw new ValidationException("First name cannot be empty");
        }
        if (firstName.length() > NAME_MAX) {
            throw new ValidationException(
                    "First name cannot exceed " + NAME_MAX + " characters");
        }
        if (!NAME_PATTERN.matcher(firstName).matches()) {
            throw new ValidationException(
                    "First name contains invalid characters");
        }
    }

    private void validateLastName(String lastName) {
        if (lastName == null || lastName.isBlank()) {
            throw new ValidationException("Last name cannot be empty");
        }
        if (lastName.length() > NAME_MAX) {
            throw new ValidationException(
                    "Last name cannot exceed " + NAME_MAX + " characters");
        }
        if (!NAME_PATTERN.matcher(lastName).matches()) {
            throw new ValidationException(
                    "Last name contains invalid characters");
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
                    "Email format is invalid — expected: user@domain.com");
        }
    }

    public void validateAddress(String address) {
        if (address == null || address.isBlank()) return; // optional field
        if (address.length() > ADDRESS_MAX) {
            throw new ValidationException(
                    "Address cannot exceed " + ADDRESS_MAX + " characters");
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Typed exceptions
    // ────────────────────────────────────────────────────────────

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