package Server.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages IP-based rate limiting to protect against brute force attacks.
 * Rules:
 * - 3 failed attempts: 15 minutes lock.
 * - After 15 mins, if 3 more failed attempts occur: 1 hour lock.
 * Persistent storage is used so that the application state retains blocks across restarts.
 */
public class AuthSecurityManager {

    private static final Logger logger = LogManager.getLogger(AuthSecurityManager.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long LOCK_15_MIN_MS = 15 * 60 * 1000L;
    private static final long LOCK_1_HOUR_MS = 60 * 60 * 1000L;
    private static final String LOG_FILE = "blocked_ips.log";

    private static class IPTracker {
        int failedAttempts = 0;
        int blockLevel = 0; // 0 = none, 1 = 15 mins, 2 = 1 hour
        long blockedUntil = 0;
    }

    private final ConcurrentHashMap<String, IPTracker> ipTrackers = new ConcurrentHashMap<>();

    private static final AuthSecurityManager instance = new AuthSecurityManager();

    private AuthSecurityManager() {
        loadTrackersFromFile();
    }

    public static AuthSecurityManager getInstance() {
        return instance;
    }

    private synchronized void loadTrackersFromFile() {
        Path path = Paths.get(LOG_FILE);
        if (!Files.exists(path)) return;

        try {
            List<String> lines = Files.readAllLines(path);
            boolean changed = false;
            for (String line : lines) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                    String ip = parts[0];
                    long blockedUntil = Long.parseLong(parts[1]);
                    int blockLevel = Integer.parseInt(parts[2]);

                    if (blockedUntil > System.currentTimeMillis()) {
                        IPTracker tracker = new IPTracker();
                        tracker.blockedUntil = blockedUntil;
                        tracker.blockLevel = blockLevel;
                        tracker.failedAttempts = MAX_ATTEMPTS; // Maxed out since it was blocked
                        ipTrackers.put(ip, tracker);
                    } else {
                        changed = true; // Expired
                    }
                }
            }
            if (changed) {
                saveTrackersToFile();
            }
        } catch (Exception e) {
            logger.error("[AuthSecurity] Failed to load blocked IPs from file", e);
        }
    }

    private synchronized void saveTrackersToFile() {
        try {
            Path path = Paths.get(LOG_FILE);
            List<String> validLines = new ArrayList<>();
            long now = System.currentTimeMillis();

            for (var entry : ipTrackers.entrySet()) {
                if (entry.getValue().blockedUntil > now) {
                    validLines.add(entry.getKey() + "|" + entry.getValue().blockedUntil + "|" + entry.getValue().blockLevel);
                }
            }
            Files.write(path, validLines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            logger.error("[AuthSecurity] Failed to save blocked IPs to file", e);
        }
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
                // First lock: 15 mins
                tracker.blockLevel = 1;
                tracker.blockedUntil = System.currentTimeMillis() + LOCK_15_MIN_MS;
                logger.warn("[AuthSecurity] IP BLOCKED for 15 minutes: " + ip);
            } else {
                // Subsequent lock: 1 hour
                tracker.blockLevel = 2;
                tracker.blockedUntil = System.currentTimeMillis() + LOCK_1_HOUR_MS;
                logger.warn("[AuthSecurity] IP BLOCKED for 1 hour: " + ip);
            }
            saveTrackersToFile();
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
            saveTrackersToFile();
        }
    }
}
