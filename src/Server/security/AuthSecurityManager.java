package Server.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages IP-based rate limiting to protect against brute force attacks.
 * Rules:
 * - 3 failed attempts: 10 minutes lock.
 * - After 10 mins, if 3 more failed attempts occur: 1 hour lock.
 */
public class AuthSecurityManager {

    private static final Logger logger = LogManager.getLogger(AuthSecurityManager.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long LOCK_10_MIN_MS = 10 * 60 * 1000L;
    private static final long LOCK_1_HOUR_MS = 60 * 60 * 1000L;

    private static class IPTracker {
        int failedAttempts = 0;
        int blockLevel = 0; // 0 = none, 1 = 10 mins, 2 = 1 hour
        long blockedUntil = 0;
    }

    private final ConcurrentHashMap<String, IPTracker> ipTrackers = new ConcurrentHashMap<>();

    private static final AuthSecurityManager instance = new AuthSecurityManager();

    private AuthSecurityManager() {}

    public static AuthSecurityManager getInstance() {
        return instance;
    }

    /**
     * Checks if the given IP address is currently blocked and returns remaining block time.
     * @return remaining block time in seconds, or 0 if not blocked.
     */
    public long getBlockRemainingSeconds(String ip) {
        if (ip == null || ip.isBlank()) return 0;
        
        IPTracker tracker = ipTrackers.get(ip);
        if (tracker == null) return 0;

        long remainingMs = tracker.blockedUntil - System.currentTimeMillis();
        if (remainingMs > 0) {
            return remainingMs / 1000;
        }
        return 0;
    }

    /**
     * Call this when a login fails for the given IP.
     * @return true if the IP just became blocked due to this failure.
     */
    public boolean handleFailedAttempt(String ip) {
        if (ip == null || ip.isBlank()) return false;

        IPTracker tracker = ipTrackers.computeIfAbsent(ip, k -> new IPTracker());

        // Do not accumulate if already blocked
        if (tracker.blockedUntil > System.currentTimeMillis()) {
            return true;
        }

        tracker.failedAttempts++;
        logger.warn("[AuthSecurity] Failed login attempt from IP: " + ip + " (" + tracker.failedAttempts + "/" + MAX_ATTEMPTS + " phase " + tracker.blockLevel + ")");

        if (tracker.failedAttempts >= MAX_ATTEMPTS) {
            tracker.failedAttempts = 0; // Reset counter for the next phase
            if (tracker.blockLevel == 0) {
                // First lock: 10 mins
                tracker.blockLevel = 1;
                tracker.blockedUntil = System.currentTimeMillis() + LOCK_10_MIN_MS;
                logger.warn("[AuthSecurity] IP BLOCKED for 10 minutes: " + ip);
            } else {
                // Subsequent lock: 1 hour
                tracker.blockLevel = 2;
                tracker.blockedUntil = System.currentTimeMillis() + LOCK_1_HOUR_MS;
                logger.warn("[AuthSecurity] IP BLOCKED for 1 hour: " + ip);
            }
            return true;
        }

        return false;
    }

    /**
     * Call this when a login completely succeeds.
     * Resets any previous tracks for this IP.
     */
    public void handleSuccessfulLogin(String ip) {
        if (ip == null || ip.isBlank()) return;
        
        IPTracker tracker = ipTrackers.remove(ip);
        if (tracker != null) {
            logger.info("[AuthSecurity] Successful login from IP: " + ip + " - Rate limits reset.");
        }
    }
}
