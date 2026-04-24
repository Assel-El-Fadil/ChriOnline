package Shared;

public class RequestParser {

    private RequestParser() {}

    public static ParsedRequest parse(String rawLine) {

        if (rawLine == null || rawLine.isBlank()) {
            throw new InvalidRequestException("Empty or null request line");
        }

        // TP7: Network Command Injection protection.
        // Strip out carriage returns, and throw an error if malicious control characters are found.
        String sanitizedLine = rawLine.replace("\r", "").replace("\n", "");
        if (sanitizedLine.matches(".*[\\x00-\\x1F].*")) {
            throw new InvalidRequestException("Request contains illegal control characters used for injection.");
        }

        String[] parts = sanitizedLine.split("\\|", -1);

        String commandToken = parts[0].trim().toUpperCase();

        Command command = Command.fromString(commandToken);

        String[] params;
        if (parts.length > 1) {
            params = new String[parts.length - 1];
            System.arraycopy(parts, 1, params, 0, parts.length - 1);
        } else {
            params = new String[0];
        }

        return new ParsedRequest(command, params);
    }

    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) { super(message); }
    }
}
