package Shared;

public final class SessionData {

    private final String token;
    private final int userId;
    private final String role;
    private final String username;
    private final String clientIP;
    private final int clientUdpPort;
    private final long createdAt;
    private long lastActivity;

    public SessionData(String token, int userId, String role,
                       String username, String clientIP, int clientUdpPort) {
        this.token = token;
        this.userId = userId;
        this.role = role;
        this.username = username;
        this.clientIP = clientIP;
        this.clientUdpPort = clientUdpPort;
        this.createdAt = System.currentTimeMillis();
        this.lastActivity = this.createdAt;
    }

    public String getToken() { return token;}
    public int getUserId() { return userId;}
    public String getRole() { return role;}
    public String getUsername() { return username;}
    public String getClientIP() { return clientIP;}
    public int getClientUdpPort() { return clientUdpPort;}
    public long getCreatedAt() { return createdAt;}
    
    public long getLastActivity() { return lastActivity;}
    public void updateLastActivity() { this.lastActivity = System.currentTimeMillis(); }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public long getAgeSeconds() {
        return (System.currentTimeMillis() - createdAt) / 1000;
    }

    public long getIdleSeconds() {
        return (System.currentTimeMillis() - lastActivity) / 1000;
    }

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
