import client.AccountClient;
import server.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class AccountIntegrationTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        MailServer server = new MailServer();
        try (ServerSocket listener = new ServerSocket(0, 20, InetAddress.getLoopbackAddress())) {
            Thread acceptor = new Thread(() -> {
                try { while (!listener.isClosed()) new Thread(new POP3ServerHandler(listener.accept(), server)).start(); }
                catch (IOException ignored) { }
            });
            acceptor.setDaemon(true); acceptor.start();
            int port = listener.getLocalPort();
            String email = "test" + System.nanoTime() + "@vku.udn.vn";
            AccountClient.execute("127.0.0.1", port, email, " secret123 ", "Người dùng thử");
            AccountClient.execute("127.0.0.1", port, email.toUpperCase(), " secret123 ", null);
            try {
                AccountClient.execute("127.0.0.1", port, email, " secret123 ", "Duplicate");
                throw new AssertionError("Duplicate registration accepted");
            } catch (IOException expected) { }
            try {
                AccountClient.execute("127.0.0.1", port, email, "wrong", null);
                throw new AssertionError("Wrong password accepted");
            } catch (IOException expected) { }
            try {
                AccountClient.execute("127.0.0.1", port, "bad-email", "123", "Invalid");
                throw new AssertionError("Invalid registration accepted");
            } catch (IOException expected) { }
            AccountClient.execute("127.0.0.1", port, "dat@vku.udn.vn", "123456", null);
            try (Socket socket = new Socket("127.0.0.1", port)) {
                socket.setSoTimeout(5000);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                in.readLine();
                out.println("USER " + email); in.readLine();
                out.println("PASS  secret123 "); check(in.readLine().startsWith("+OK"), "Login failed");
                out.println("USER someone@vku.udn.vn"); in.readLine();
                out.println("STAT"); check(in.readLine().startsWith("-ERR"), "USER retained authentication");
            }
            try (java.sql.Connection db = java.sql.DriverManager.getConnection("jdbc:sqlite:mail_database.db");
                 java.sql.Statement stmt = db.createStatement();
                 java.sql.ResultSet rows = stmt.executeQuery("SELECT password FROM mail_accounts")) {
                while (rows.next()) check(rows.getString(1).startsWith("pbkdf2$"), "Plaintext password stored");
            }
            System.out.println("PASS: registration, duplicate/invalid rejection, login, wrong password, session reset, hashed storage");
        } finally { server.dispose(); }
        System.exit(0);
    }
}
