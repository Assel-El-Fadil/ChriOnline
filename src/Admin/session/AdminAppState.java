package Admin.session;

public class AdminAppState {

    private static String token;
    private static String username;
    private static String role;
    private static int userId;

    public static void setSession(String token, String username, String role, int userId) {
        AdminAppState.token = token;
        AdminAppState.username = username;
        AdminAppState.role = role;
        AdminAppState.userId = userId;
    }

    public static String getToken() {
        return token;
    }

    public static void updateToken(String newToken) {
        AdminAppState.token = newToken;
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
