package server;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class POP3ServerHandler implements Runnable {
    private Socket socket;
    private MailServer server;
    private BufferedReader reader;
    private BufferedWriter writer;

    private String userEmail = "";
    private boolean isAuthenticated = false;
    private List<MailDatabase.EmailRecord> userEmails = new ArrayList<>();
    private boolean isRunning = true;

    public POP3ServerHandler(Socket socket, MailServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));

            sendResponse("+OK VKU POP3 Server ready (RFC 1939)");

            while (isRunning) {
                String line = reader.readLine();
                if (line == null) break;
                if (line.trim().isEmpty()) continue;

                server.log("[POP3] " + socket.getInetAddress().getHostAddress() + " -> " + (line.toUpperCase().startsWith("PASS ") || line.toUpperCase().startsWith("REGISTER ") ? "[credentials hidden]" : line));
                processCommand(line);
            }
        } catch (Exception ignored) {
        } finally {
            close();
        }
    }

    private void processCommand(String line) throws IOException {
        String[] parts = line.split(" ", 2);
        String cmd = parts[0].toUpperCase();
        String arg = parts.length > 1 ? (cmd.equals("PASS") ? parts[1] : parts[1].trim()) : "";

        switch (cmd) {
            case "REGISTER":
                if (isAuthenticated) { sendResponse("-ERR Sign out before registration"); break; }
                try {
                    String[] fields = arg.split(" ", -1);
                    if (fields.length != 3) throw new IllegalArgumentException();
                    java.util.Base64.Decoder decoder = java.util.Base64.getDecoder();
                    String email = new String(decoder.decode(fields[0]), java.nio.charset.StandardCharsets.UTF_8).trim().toLowerCase(java.util.Locale.ROOT);
                    String password = new String(decoder.decode(fields[1]), java.nio.charset.StandardCharsets.UTF_8);
                    String name = new String(decoder.decode(fields[2]), java.nio.charset.StandardCharsets.UTF_8).trim();
                    if (!email.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") || password.length() < 6 || password.length() > 128 || name.isEmpty() || name.length() > 100)
                        throw new IllegalArgumentException();
                    sendResponse(server.getDatabase().registerAccount(email, password, name)
                            ? "+OK Account created" : "-ERR Email da ton tai hoac database khong san sang");
                } catch (IllegalArgumentException e) { sendResponse("-ERR Thong tin dang ky khong hop le (mat khau 6-128 ky tu)"); }
                break;
            case "USER":
                isAuthenticated = false;
                userEmails.clear();
                this.userEmail = arg;
                sendResponse("+OK Name is a valid mailbox");
                break;

            case "PASS":
                isAuthenticated = false;
                userEmails.clear();
                if (server.getDatabase().authenticate(userEmail, arg)) {
                    isAuthenticated = true;
                    userEmails = server.getDatabase().getInbox(userEmail);
                    sendResponse("+OK Mailbox locked and ready (" + userEmails.size() + " messages)");
                    server.log("[POP3 AUTH] Đăng nhập POP3 thành công: " + userEmail);
                } else {
                    sendResponse("-ERR Invalid password or user not found");
                }
                break;

            case "STAT":
                if (!isAuthenticated) {
                    sendResponse("-ERR Please authenticate first");
                    return;
                }
                long totalSize = 0;
                for (MailDatabase.EmailRecord r : userEmails) totalSize += r.size;
                sendResponse("+OK " + userEmails.size() + " " + totalSize);
                break;

            case "LIST":
                if (!isAuthenticated) {
                    sendResponse("-ERR Please authenticate first");
                    return;
                }
                sendResponse("+OK " + userEmails.size() + " messages");
                for (int i = 0; i < userEmails.size(); i++) {
                    MailDatabase.EmailRecord r = userEmails.get(i);
                    writer.write((i + 1) + " " + r.size + "\r\n");
                }
                writer.write(".\r\n");
                writer.flush();
                break;

            case "RETR":
                if (!isAuthenticated) {
                    sendResponse("-ERR Please authenticate first");
                    return;
                }
                try {
                    int index = Integer.parseInt(arg) - 1;
                    if (index >= 0 && index < userEmails.size()) {
                        MailDatabase.EmailRecord r = userEmails.get(index);
                        sendResponse("+OK " + r.size + " octets");
                        writer.write("From: " + r.sender + "\r\n");
                        writer.write("To: " + r.recipient + "\r\n");
                        writer.write("Subject: " + r.subject + "\r\n");
                        writer.write("Date: " + r.date + "\r\n");
                        writer.write("\r\n");
                        writer.write(r.content + "\r\n");
                        writer.write(".\r\n");
                        writer.flush();
                    } else {
                        sendResponse("-ERR Invalid message number");
                    }
                } catch (Exception ex) {
                    sendResponse("-ERR " + ex.getMessage());
                }
                break;

            case "DELE":
                if (!isAuthenticated) {
                    sendResponse("-ERR Please authenticate first");
                    return;
                }
                try {
                    int index = Integer.parseInt(arg) - 1;
                    if (index >= 0 && index < userEmails.size()) {
                        MailDatabase.EmailRecord r = userEmails.remove(index);
                        server.getDatabase().deleteEmail(r.id);
                        sendResponse("+OK message " + arg + " deleted");
                    } else {
                        sendResponse("-ERR Invalid message number");
                    }
                } catch (Exception ex) {
                    sendResponse("-ERR " + ex.getMessage());
                }
                break;

            case "NOOP":
                sendResponse("+OK");
                break;

            case "QUIT":
                sendResponse("+OK VKU POP3 server signing off");
                isRunning = false;
                break;

            default:
                sendResponse("-ERR Unknown command");
                break;
        }
    }

    private void sendResponse(String resp) throws IOException {
        writer.write(resp + "\r\n");
        writer.flush();
        server.log("[POP3 RESP] -> " + resp);
    }

    public void close() {
        isRunning = false;
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
