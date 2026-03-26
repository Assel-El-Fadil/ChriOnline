package Server.DAO;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedList;

public class ConnectionPool {

    private static final int POOL_SIZE = 5;

    private static final LinkedList<Connection> pool = new LinkedList<>();

    private static final String dbUrl      = "jdbc:mysql://localhost:3306/chrionline";
    private static final String dbUser     = "root";
    private static final String dbPassword = "";

    static {
        initializePool();
    }

    private static void initializePool() {
        for (int i = 0; i < POOL_SIZE; i++) {
            try {
                Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
                pool.addLast(conn);
            } catch (SQLException e) {
                throw new RuntimeException(
                        "[ConnectionPool] FATAL: Could not open connection "
                                + (i + 1) + "/" + POOL_SIZE + " — " + e.getMessage(), e);
            }
        }
        System.out.println("[ConnectionPool] Initialized — " + POOL_SIZE + " connections ready.");
    }

    public static Connection getConnection() {
        synchronized (pool) {
            while (pool.isEmpty()) {
                try {
                    pool.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(
                            "[ConnectionPool] Interrupted while waiting for a connection", e);
                }
            }

            Connection conn = pool.removeFirst();

            try {
                if (conn == null || conn.isClosed() || !conn.isValid(2)) {
                    System.out.println("[ConnectionPool] Stale connection detected — replacing.");
                    conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
                }
            } catch (SQLException e) {
                throw new RuntimeException(
                        "[ConnectionPool] Could not replace stale connection — " + e.getMessage(), e);
            }

            return conn;
        }
    }

    public static void returnConnection(Connection conn) {
        if (conn == null) return;

        synchronized (pool) {
            try {
                if (!conn.getAutoCommit()) {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                System.err.println(
                        "[ConnectionPool] Could not reset auto-commit — discarding connection: "
                                + e.getMessage());
                try {
                    conn.close();
                    conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
                } catch (SQLException ex) {
                    System.err.println(
                            "[ConnectionPool] Could not replace discarded connection: "
                                    + ex.getMessage());
                    pool.notifyAll();
                    return;
                }
            }

            pool.addLast(conn);
            pool.notifyAll();
        }
    }

    public static int availableConnections() {
        synchronized (pool) {
            return pool.size();
        }
    }
}