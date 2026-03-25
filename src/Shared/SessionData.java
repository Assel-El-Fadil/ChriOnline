package Shared;

public final class SessionData {

    private final String token;
    private final int userId;
    private final String role;
    private final String username;
    private final String clientIP;
    private final int clientUdpPort;
    private final long createdAt;

    // ────────────────────────────────────────────────────────────
    //  Constructor
    // ────────────────────────────────────────────────────────────

    /**
     * Creates a new SessionData snapshot.
     * Called by AuthHandler immediately after successful login.
     *
     * @param token         UUID string — the session identifier
     * @param userId        PK from the users table
     * @param role          "USER" or "ADMIN"
     * @param username      login name — for logging and display
     * @param clientIP      IP address from Socket.getInetAddress().getHostAddress()
     * @param clientUdpPort UDP port the client sent as the last LOGIN param
     */
    public SessionData(String token, int userId, String role,
                       String username, String clientIP, int clientUdpPort) {
        this.token         = token;
        this.userId        = userId;
        this.role          = role;
        this.username      = username;
        this.clientIP      = clientIP;
        this.clientUdpPort = clientUdpPort;
        this.createdAt     = System.currentTimeMillis();
    }

    // ────────────────────────────────────────────────────────────
    //  Accessors
    // ────────────────────────────────────────────────────────────

    public String getToken()         { return token;         }
    public int    getUserId()        { return userId;        }
    public String getRole()          { return role;          }
    public String getUsername()      { return username;      }
    public String getClientIP()      { return clientIP;      }
    public int    getClientUdpPort() { return clientUdpPort; }
    public long   getCreatedAt()     { return createdAt;     }

    // ────────────────────────────────────────────────────────────
    //  Convenience helpers
    // ────────────────────────────────────────────────────────────

    /**
     * Returns true if this session belongs to an ADMIN-role user.
     * Used by AdminHandler.requireAdmin() — a single method call
     * instead of a string comparison scattered across every handler.
     */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    /**
     * Returns how many seconds this session has been alive.
     * Calculated from createdAt — no DB query needed.
     * Useful for logging: "Session alive for 142s before disconnect."
     */
    public long getAgeSeconds() {
        return (System.currentTimeMillis() - createdAt) / 1000;
    }

    // ────────────────────────────────────────────────────────────
    //  Object overrides
    // ────────────────────────────────────────────────────────────

    /**
     * Safe to log — shows userId, role, IP, and age.
     * Deliberately omits the token string to avoid leaking it into
     * log files where it could be extracted and replayed.
     */
    @Override
    public String toString() {
        return "SessionData{"
                + "userId="    + userId
                + ", username='"+ username + "'"
                + ", role='"   + role + "'"
                + ", ip='"     + clientIP + "'"
                + ", udpPort=" + clientUdpPort
                + ", age="     + getAgeSeconds() + "s"
                + "}";
    }
}
