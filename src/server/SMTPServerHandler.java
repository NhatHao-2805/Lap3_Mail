package server;

import java.io.*;
import java.net.Socket;

public class SMTPServerHandler implements Runnable {
    private Socket socket;
    private MailServer server;
    private BufferedReader reader;
    private BufferedWriter writer;

    private String senderEmail = "";
    private String recipientEmail = "";
    private boolean isRunning = true;

    public SMTPServerHandler(Socket socket, MailServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            sendResponse("220 vku.udn.vn ESMTP Service ready");

            while (isRunning) {
                String line = reader.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                server.log("[SMTP] " + socket.getInetAddress().getHostAddress() + " -> " + line);
                processCommand(line);
            }
        } catch (Exception ignored) {
        } finally {
            close();
        }
    }

    private void processCommand(String line) throws IOException {
        String upper = line.toUpperCase();

        if (upper.startsWith("HELO") || upper.startsWith("EHLO")) {
            sendResponse("250-vku.udn.vn Hello " + socket.getInetAddress().getHostAddress() + "\r\n250 8BITMIME");
        } else if (upper.startsWith("MAIL FROM:")) {
            senderEmail = extractEmail(line.substring(10).trim());
            sendResponse("250 2.1.0 Sender " + senderEmail + " OK");
        } else if (upper.startsWith("RCPT TO:")) {
            recipientEmail = extractEmail(line.substring(8).trim());
            sendResponse("250 2.1.5 Recipient " + recipientEmail + " OK");
        } else if (upper.equals("DATA")) {
            sendResponse("354 Start mail input; end with <CRLF>.<CRLF>");
            readMailContent();
        } else if (upper.equals("RSET")) {
            senderEmail = "";
            recipientEmail = "";
            sendResponse("250 2.0.0 Reset OK");
        } else if (upper.equals("NOOP")) {
            sendResponse("250 2.0.0 OK");
        } else if (upper.equals("QUIT")) {
            sendResponse("221 2.0.0 vku.udn.vn closing connection");
            isRunning = false;
        } else {
            sendResponse("500 5.5.1 Command unrecognized: " + line);
        }
    }

    private void readMailContent() throws IOException {
        StringBuilder fullMail = new StringBuilder();
        String subject = "(Không có tiêu đề)";
        boolean inHeaders = true;
        StringBuilder body = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            if (".".equals(line)) {
                break; // End of DATA
            }
            fullMail.append(line).append("\r\n");

            if (inHeaders) {
                if (line.isEmpty()) {
                    inHeaders = false;
                } else if (line.toLowerCase().startsWith("subject:")) {
                    subject = line.substring(8).trim();
                }
            } else {
                body.append(line).append("\r\n");
            }
        }

        String finalContent = body.length() > 0 ? body.toString() : fullMail.toString();
        boolean saved = server.getDatabase().saveEmail(senderEmail, recipientEmail, subject, finalContent);

        if (saved) {
            sendResponse("250 2.0.0 OK: message queued for delivery");
            server.log("[SMTP SUCCESS] Đã lưu thư từ <" + senderEmail + "> gửi tới <" + recipientEmail + ">: \"" + subject + "\"");
        } else {
            sendResponse("451 4.3.0 Local error in processing");
        }
    }

    private String extractEmail(String raw) {
        int start = raw.indexOf('<');
        int end = raw.indexOf('>');
        if (start != -1 && end > start) {
            return raw.substring(start + 1, end).trim();
        }
        return raw.trim();
    }

    private void sendResponse(String resp) throws IOException {
        writer.write(resp + "\r\n");
        writer.flush();
        server.log("[SMTP RESP] -> " + resp.replace("\r\n", " | "));
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
