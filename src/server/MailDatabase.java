package server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MailDatabase {
    private static final String DB_URL = "jdbc:sqlite:mail_database.db";
    private Connection conn;

    public static class EmailRecord {
        public int id;
        public String sender;
        public String recipient;
        public String subject;
        public String content;
        public String date;
        public boolean isRead;
        public long size;

        public EmailRecord(int id, String sender, String recipient, String subject, String content, String date, boolean isRead, long size) {
            this.id = id;
            this.sender = sender;
            this.recipient = recipient;
            this.subject = subject;
            this.content = content;
            this.date = date;
            this.isRead = isRead;
            this.size = size;
        }
    }

    public MailDatabase() {
        initDatabase();
    }

    private void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(DB_URL);
            createTables();
            seedDefaultAccounts();
            System.out.println("[Mail DB] Kết nối SQLite thành công: mail_database.db");
        } catch (Exception e) {
            System.err.println("[Mail DB] Lỗi khởi tạo SQLite: " + e.getMessage());
        }
    }

    private void createTables() {
        if (conn == null) return;
        String sqlUsers = "CREATE TABLE IF NOT EXISTS mail_accounts (" +
                "email TEXT PRIMARY KEY, " +
                "password TEXT, " +
                "full_name TEXT, " +
                "created_at TEXT);";

        String sqlEmails = "CREATE TABLE IF NOT EXISTS emails (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender TEXT, " +
                "recipient TEXT, " +
                "subject TEXT, " +
                "content TEXT, " +
                "date TEXT, " +
                "is_read INTEGER DEFAULT 0, " +
                "size INTEGER);";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sqlUsers);
            stmt.execute(sqlEmails);
        } catch (SQLException e) {
            System.err.println("[Mail DB] Lỗi tạo bảng: " + e.getMessage());
        }
    }

    private void seedDefaultAccounts() {
        registerAccount("dat@vku.udn.vn", "123456", "Hà Quốc Đạt");
        registerAccount("friend@vku.udn.vn", "123456", "Người Bạn Thân");
        registerAccount("admin@vku.udn.vn", "admin123", "Quản trị viên VKU");

        // Seed initial welcome email
        if (getEmailCount("dat@vku.udn.vn") == 0) {
            saveEmail("admin@vku.udn.vn", "dat@vku.udn.vn", "Chào mừng bạn đến với Hệ thống Mail VKU",
                    "Xin chào Hà Quốc Đạt,\r\n\r\nChúc mừng bạn đã hoàn thành kết nối hệ thống Mail Server (SMTP & POP3).\r\nBạn có thể thử gửi thư nội bộ hoặc gửi thư tới Gmail thật bằng ứng dụng này!\r\n\r\nTrân trọng,\r\nBan Quản Trị Mạng VKU");
        }
    }

    public synchronized boolean registerAccount(String email, String password, String fullName) {
        if (conn == null) return false;
        String sql = "INSERT OR IGNORE INTO mail_accounts (email, password, full_name, created_at) " +
                "VALUES (?, ?, ?, datetime('now', 'localtime'))";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email.toLowerCase(java.util.Locale.ROOT));
            pstmt.setString(2, hashPassword(password));
            pstmt.setString(3, fullName);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean authenticate(String email, String password) {
        if (conn == null) return false;
        String sql = "SELECT password FROM mail_accounts WHERE email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email.toLowerCase(java.util.Locale.ROOT));
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String stored = rs.getString("password");
                if (stored != null && stored.startsWith("pbkdf2$")) return verifyPassword(password, stored);
                if (!password.equals(stored)) return false;
                try (PreparedStatement update = conn.prepareStatement("UPDATE mail_accounts SET password = ? WHERE email = ?")) {
                    update.setString(1, hashPassword(password));
                    update.setString(2, email.toLowerCase(java.util.Locale.ROOT));
                    update.executeUpdate();
                }
                return true;
            }
        } catch (SQLException ignored) {}
        return false;
    }

    private static String hashPassword(String password) {
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        return "pbkdf2$" + java.util.Base64.getEncoder().encodeToString(salt) + "$" +
                java.util.Base64.getEncoder().encodeToString(derive(password, salt));
    }

    private static byte[] derive(String password, byte[] salt) {
        javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 120000, 256);
        try {
            return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        } finally { spec.clearPassword(); }
    }

    private static boolean verifyPassword(String password, String stored) {
        try {
            String[] parts = stored.split("\\$");
            return java.security.MessageDigest.isEqual(derive(password, java.util.Base64.getDecoder().decode(parts[1])),
                    java.util.Base64.getDecoder().decode(parts[2]));
        } catch (RuntimeException e) { return false; }
    }

    public synchronized boolean saveEmail(String sender, String recipient, String subject, String content) {
        if (conn == null) return false;
        String sql = "INSERT INTO emails (sender, recipient, subject, content, date, size) " +
                "VALUES (?, ?, ?, ?, datetime('now', 'localtime'), ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, recipient.toLowerCase());
            pstmt.setString(3, subject);
            pstmt.setString(4, content);
            pstmt.setLong(5, content.getBytes().length);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[Mail DB] saveEmail error: " + e.getMessage());
            return false;
        }
    }

    public synchronized List<EmailRecord> getInbox(String recipientEmail) {
        List<EmailRecord> list = new ArrayList<>();
        if (conn == null) return list;
        String sql = "SELECT * FROM emails WHERE recipient = ? ORDER BY id DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, recipientEmail.toLowerCase());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new EmailRecord(
                        rs.getInt("id"),
                        rs.getString("sender"),
                        rs.getString("recipient"),
                        rs.getString("subject"),
                        rs.getString("content"),
                        rs.getString("date"),
                        rs.getInt("is_read") == 1,
                        rs.getLong("size")
                ));
            }
        } catch (SQLException ignored) {}
        return list;
    }

    public synchronized EmailRecord getEmailById(int id) {
        if (conn == null) return null;
        String sql = "SELECT * FROM emails WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new EmailRecord(
                        rs.getInt("id"),
                        rs.getString("sender"),
                        rs.getString("recipient"),
                        rs.getString("subject"),
                        rs.getString("content"),
                        rs.getString("date"),
                        rs.getInt("is_read") == 1,
                        rs.getLong("size")
                );
            }
        } catch (SQLException ignored) {}
        return null;
    }

    public synchronized int getEmailCount(String recipientEmail) {
        if (conn == null) return 0;
        String sql = "SELECT COUNT(*) FROM emails WHERE recipient = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, recipientEmail.toLowerCase());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException ignored) {}
        return 0;
    }

    public synchronized boolean deleteEmail(int id) {
        if (conn == null) return false;
        String sql = "DELETE FROM emails WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException ignored) {}
        return false;
    }
}
