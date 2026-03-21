package Client.session;

public class AppState {

    private static String token;
    private static String username;
    private static String role;
    private static int userId;

    public static void setSession(String token, String username, String role, int userId) {
        AppState.token = token;
        AppState.username = username;
        AppState.role = role;
        AppState.userId = userId;
    }

    public static String getToken() {
        return token;
    }

    public static int getUserId() {
        return userId;
    }

    public static String getUsername() {
        return username;
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public static void clear() {
        token = null;
        username = null;
        role = null;
        userId = 0;
    }
}
