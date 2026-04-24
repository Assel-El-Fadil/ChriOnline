package Server;
import Shared.SessionData;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SessionManager {

    private static final Logger logger = LogManager.getLogger(SessionManager.class);
    
    // 10 minutes in seconds
    private static final long MAX_IDLE_TIME_SECONDS = 10 * 60;

    private final ConcurrentHashMap<String, SessionData> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    public SessionManager() {
        cleanupExecutor.scheduleAtFixedRate(this::cleanupIdleSessions, 1, 1, TimeUnit.MINUTES);
    }
    
    private void cleanupIdleSessions() {
        sessions.forEach((token, session) -> {
            if (session.getIdleSeconds() > MAX_IDLE_TIME_SECONDS) {
                logger.warn("[SessionManager] AFK Timeout: Removing idle session for user " + session.getUsername());
                removeSession(token);
            }
        });
    }

    public void stopCleanup() {
        cleanupExecutor.shutdownNow();
    }

    // ────────────────────────────────────────────────────────────
    //  Write operations
    // ────────────────────────────────────────────────────────────

    public void addSession(String token, SessionData data) {
        if (token == null || token.isBlank() || data == null) {
            throw new IllegalArgumentException(
                    "SessionManager.addSession: token and data must not be null");
        }
        sessions.put(token, data);
        logger.info("[SessionManager] Session added — " + data
                + "  | Active sessions: " + sessions.size());
    }

    public void removeSession(String token) {
        if (token == null || token.isBlank()) return;

        SessionData removed = sessions.remove(token);
        if (removed != null) {
            logger.info("[SessionManager] Session removed — "
                    + removed.getUsername()
                    + " (alive " + removed.getAgeSeconds() + "s)"
                    + "  | Active sessions: " + sessions.size());
        }
    }

    public String regenerateToken(String oldToken) {
        SessionData data = sessions.remove(oldToken);
        if (data == null) {
            return null;
        }

        String newToken = java.util.UUID.randomUUID().toString();
        SessionData newData = new SessionData(
                newToken,
                data.getUserId(),
                data.getRole(),
                data.getUsername(),
                data.getClientIP(),
                data.getClientUdpPort()
        );
        newData.updateLastActivity();

        sessions.put(newToken, newData);
        logger.info("[SessionManager] Token regenerated for user " + data.getUsername());
        return newToken;
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
        logger.info("[SessionManager] All sessions cleared ("
                + count + " removed).");
    }
}
