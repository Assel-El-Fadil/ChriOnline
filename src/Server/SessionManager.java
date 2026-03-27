package Server;
import Shared.SessionData;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final ConcurrentHashMap<String, SessionData> sessions =
            new ConcurrentHashMap<>();

    // ────────────────────────────────────────────────────────────
    //  Write operations
    // ────────────────────────────────────────────────────────────

    public void addSession(String token, SessionData data) {
        if (token == null || token.isBlank() || data == null) {
            throw new IllegalArgumentException(
                    "SessionManager.addSession: token and data must not be null");
        }
        sessions.put(token, data);
        System.out.println("[SessionManager] Session added — " + data
                + "  | Active sessions: " + sessions.size());
    }

    public void removeSession(String token) {
        if (token == null || token.isBlank()) return;

        SessionData removed = sessions.remove(token);
        if (removed != null) {
            System.out.println("[SessionManager] Session removed — "
                    + removed.getUsername()
                    + " (alive " + removed.getAgeSeconds() + "s)"
                    + "  | Active sessions: " + sessions.size());
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Read operations
    // ────────────────────────────────────────────────────────────

    public SessionData getSession(String token) {
        if (token == null || token.isBlank()) return null;
        return sessions.get(token);
    }

    public int getUserId(String token) {
        SessionData session = getSession(token);
        return session != null ? session.getUserId() : -1;
    }

    public String getRole(String token) {
        SessionData session = getSession(token);
        return session != null ? session.getRole() : null;
    }

    public String getClientIP(String token) {
        SessionData session = getSession(token);
        return session != null ? session.getClientIP() : null;
    }

    public int getClientUdpPort(String token) {
        SessionData session = getSession(token);
        return session != null ? session.getClientUdpPort() : -1;
    }

    public boolean isAdmin(String token) {
        SessionData session = getSession(token);
        return session != null && session.isAdmin();
    }

    // ────────────────────────────────────────────────────────────
    //  Diagnostics
    // ────────────────────────────────────────────────────────────

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public List<SessionData> getAllSessions() {
        return List.copyOf(sessions.values());
    }

    public void clearAll() {
        int count = sessions.size();
        sessions.clear();
        System.out.println("[SessionManager] All sessions cleared ("
                + count + " removed).");
    }
}
