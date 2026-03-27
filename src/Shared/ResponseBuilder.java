package Shared;

public class ResponseBuilder {

    private static final String STATUS_OK  = "OK";
    private static final String STATUS_ERR = "ERR";
    private static final String DELIMITER  = "|";

    private ResponseBuilder() {}

    public static String ok(String payload) {
        if (payload == null) {
            return STATUS_OK + DELIMITER;
        }
        return STATUS_OK + DELIMITER + payload;
    }

    public static String ok() {
        return STATUS_OK + DELIMITER;
    }

    public static String error(String message) {
        if (message == null || message.isBlank()) {
            return STATUS_ERR + DELIMITER + "An unknown error occurred";
        }
        return STATUS_ERR + DELIMITER + message;
    }

    public static boolean isOk(String responseLine) {
        return responseLine != null && responseLine.startsWith(STATUS_OK + DELIMITER);
    }

    public static String extractPayload(String responseLine) {
        if (responseLine == null) return "";
        int pipeIndex = responseLine.indexOf(DELIMITER);
        if (pipeIndex == -1 || pipeIndex == responseLine.length() - 1) {
            return "";
        }
        return responseLine.substring(pipeIndex + 1);
    }

    public static String extractError(String responseLine) {
        return extractPayload(responseLine);
    }
}
