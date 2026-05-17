package Server;
import Shared.SessionData;
import Shared.Security.CryptoConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    // Tokens are hashed (SHA-256) before being used as map keys.
    // This means raw tokens never appear in memory as HashMap keys,
    // protecting against memory-dump attacks.
    private final ConcurrentHashMap<String, SessionData> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    public SessionManager() {
        cleanupExecutor.scheduleAtFixedRate(this::cleanupIdleSessions, 1, 1, TimeUnit.MINUTES);
    }
    
    private void cleanupIdleSessions() {
        sessions.forEach((hashedToken, session) -> {
            if (session.getIdleSeconds() > MAX_IDLE_TIME_SECONDS) {
                logger.warn("[SessionManager] AFK Timeout: Removing idle session for user " + session.getUsername());
                sessions.remove(hashedToken);
            }
        });
    }

    public void stopCleanup() {
        cleanupExecutor.shutdownNow();
    }

    // ────────────────────────────────────────────────────────────
    //  Token hashing - SHA-256
    // ────────────────────────────────────────────────────────────

    /**
     * Hashes a raw token using SHA-256 so the plaintext token
     * never appears as a key in the sessions map.
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(CryptoConfig.TOKEN_HASH_ALGORITHM);
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            // Convert to hex string
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JVM
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Write operations
    // ────────────────────────────────────────────────────────────

    public void addSession(String token, SessionData data) {
        if (token == null || token.isBlank() || data == null) {
            throw new IllegalArgumentException(
                    "SessionManager.addSession: token and data must not be null");
        }
        String hashedKey = hashToken(token);
        sessions.put(hashedKey, data);
        logger.info("[SessionManager] Session added - " + data
                + "  | Active sessions: " + sessions.size());
    }

    public void removeSession(String token) {
        if (token == null || token.isBlank()) return;

        String hashedKey = hashToken(token);
        SessionData removed = sessions.remove(hashedKey);
        if (removed != null) {
            logger.info("[SessionManager] Session removed - "
                    + removed.getUsername()
                    + " (alive " + removed.getAgeSeconds() + "s)"
                    + "  | Active sessions: " + sessions.size());
        }
    }

    public String regenerateToken(String oldToken) {
        String oldHashedKey = hashToken(oldToken);
        SessionData data = sessions.remove(oldHashedKey);
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

        String newHashedKey = hashToken(newToken);
        sessions.put(newHashedKey, newData);
        logger.info("[SessionManager] Token regenerated for user " + data.getUsername());
        return newToken;
    }

    // ────────────────────────────────────────────────────────────
    //  Read operations
    // ────────────────────────────────────────────────────────────

    public SessionData getSession(String token) {
        if (token == null || token.isBlank()) return null;
        return sessions.get(hashToken(token));
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
