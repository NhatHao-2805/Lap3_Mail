package server;

import common.Message;
import common.MessageType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:chat_database.db";
    private Connection conn;

    public DatabaseManager() {
        initDatabase();
    }

    private void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(DB_URL);
            createTables();
            System.out.println("[DB] SQLite database connected successfully: chat_database.db");
        } catch (ClassNotFoundException e) {
            System.err.println("[DB WARNING] SQLite JDBC Driver not found in classpath. Messages will not be persisted to SQLite.");
        } catch (SQLException e) {
            System.err.println("[DB ERROR] Error connecting to SQLite: " + e.getMessage());
        }
    }

    private void createTables() {
        if (conn == null) return;
        String sqlUsers = "CREATE TABLE IF NOT EXISTS users (" +
                "username TEXT PRIMARY KEY, " +
                "password TEXT, " +
                "created_at TEXT);";

        String sqlMessages = "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender TEXT, " +
                "receiver TEXT, " +
                "message_type TEXT, " +
                "content TEXT, " +
                "file_name TEXT, " +
                "timestamp TEXT);";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sqlUsers);
            stmt.execute(sqlMessages);
        } catch (SQLException e) {
            System.err.println("[DB ERROR] Error creating tables: " + e.getMessage());
        }
    }

    public synchronized boolean registerOrLoginUser(String username, String password) {
        if (conn == null) return true; // allow if no DB
        try {
            String checkSql = "SELECT password FROM users WHERE username = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
                pstmt.setString(1, username);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    // User exists, verify password
                    String storedPwd = rs.getString("password");
                    return storedPwd.equals(password);
                }
            }

            // Register new user
            String insertSql = "INSERT INTO users (username, password, created_at) VALUES (?, ?, datetime('now', 'localtime'))";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setString(1, username);
                pstmt.setString(2, password);
                pstmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[DB ERROR] registerOrLoginUser: " + e.getMessage());
            return true;
        }
    }

    public synchronized void saveMessage(Message msg) {
        if (conn == null) return;
        String sql = "INSERT INTO messages (sender, receiver, message_type, content, file_name, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, msg.getSender());
            pstmt.setString(2, msg.getReceiver());
            pstmt.setString(3, msg.getType().name());
            pstmt.setString(4, msg.getContent());
            pstmt.setString(5, msg.getFileName());
            pstmt.setString(6, msg.getTimestamp());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB ERROR] saveMessage: " + e.getMessage());
        }
    }

    public synchronized List<Message> getMessageHistory(int limit) {
        List<Message> history = new ArrayList<>();
        if (conn == null) return history;
        String sql = "SELECT * FROM (SELECT * FROM messages ORDER BY id DESC LIMIT ?) ORDER BY id ASC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String sender = rs.getString("sender");
                String receiver = rs.getString("receiver");
                MessageType type = MessageType.valueOf(rs.getString("message_type"));
                String content = rs.getString("content");
                String fileName = rs.getString("file_name");
                String timestamp = rs.getString("timestamp");

                Message msg = new Message(type, sender, receiver, content);
                msg.setFileName(fileName);
                msg.setTimestamp(timestamp);
                history.add(msg);
            }
        } catch (Exception e) {
            System.err.println("[DB ERROR] getMessageHistory: " + e.getMessage());
        }
        return history;
    }
}
