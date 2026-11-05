package client;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

public class SMTPClient {
    private Consumer<String> logListener;

    public SMTPClient(Consumer<String> logListener) {
        this.logListener = logListener;
    }

    private void log(String s) {
        if (logListener != null) logListener.accept(s);
    }

    /**
     * Send email to Localhost/LAN Mail Server (port 2525)
     */
    public boolean sendLocalEmail(String host, int port, String from, String to, String subject, String body) {
        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            log("[SMTP Local] Đang kết nối tới " + host + ":" + port + "...");
            String resp = reader.readLine();
            log("S: " + resp);
            if (!resp.startsWith("220")) return false;

            sendCmd(writer, reader, "EHLO client.vku.udn.vn");
            sendCmd(writer, reader, "MAIL FROM:<" + from + ">");
            sendCmd(writer, reader, "RCPT TO:<" + to + ">");
            sendCmd(writer, reader, "DATA");

            // Write email content
            writer.write("From: " + from + "\r\n");
            writer.write("To: " + to + "\r\n");
            writer.write("Subject: " + subject + "\r\n");
            writer.write("Content-Type: text/plain; charset=UTF-8\r\n");
            writer.write("\r\n");
            writer.write(body + "\r\n");
            writer.write(".\r\n");
            writer.flush();

            resp = reader.readLine();
            log("S: " + resp);

            sendCmd(writer, reader, "QUIT");
            return resp != null && resp.startsWith("250");
        } catch (Exception e) {
            log("[SMTP Local Lỗi] " + e.getMessage());
            return false;
        }
    }

    /**
     * Send email via real Gmail SMTP (smtp.gmail.com:465 with SSL)
     * Requires Gmail App Password
     */
    public boolean sendGmailSSL(String gmailUser, String appPassword, String to, String subject, String body) {
        String host = "smtp.gmail.com";
        int port = 465;

        try {
            log("[Gmail SMTP] Đang kết nối SSL tới " + host + ":" + port + "...");
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

                String resp = reader.readLine();
                log("Gmail: " + resp);

                sendCmd(writer, reader, "EHLO " + host);
                sendCmd(writer, reader, "AUTH LOGIN");

                // Base64 username
                String userB64 = Base64.getEncoder().encodeToString(gmailUser.getBytes(StandardCharsets.UTF_8));
                sendCmd(writer, reader, userB64);

                // Base64 password
                String passB64 = Base64.getEncoder().encodeToString(appPassword.getBytes(StandardCharsets.UTF_8));
                String authResp = sendCmd(writer, reader, passB64);
                if (!authResp.startsWith("235")) {
                    log("[Gmail Lỗi] Xác thực thất bại! Hãy chắc chắn bạn dùng App Password (Mật khẩu ứng dụng 16 ký tự của Google).");
                    return false;
                }

                sendCmd(writer, reader, "MAIL FROM:<" + gmailUser + ">");
                sendCmd(writer, reader, "RCPT TO:<" + to + ">");
                sendCmd(writer, reader, "DATA");

                writer.write("From: " + gmailUser + "\r\n");
                writer.write("To: " + to + "\r\n");
                writer.write("Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=\r\n");
                writer.write("Content-Type: text/plain; charset=UTF-8\r\n");
                writer.write("\r\n");
                writer.write(body + "\r\n");
                writer.write(".\r\n");
                writer.flush();

                resp = reader.readLine();
                log("Gmail: " + resp);

                sendCmd(writer, reader, "QUIT");
                return resp != null && resp.startsWith("250");
            }
        } catch (Exception e) {
            log("[Gmail Lỗi] " + e.getMessage());
            return false;
        }
    }

    private String sendCmd(BufferedWriter writer, BufferedReader reader, String cmd) throws IOException {
        log("C: " + (cmd.length() > 30 && !cmd.startsWith("EHLO") ? cmd.substring(0, 15) + "..." : cmd));
        writer.write(cmd + "\r\n");
        writer.flush();

        String resp = reader.readLine();
        log("S: " + resp);
        // Handle multiline reply (e.g., 250-...)
        while (resp != null && resp.length() >= 4 && resp.charAt(3) == '-') {
            resp = reader.readLine();
            log("S: " + resp);
        }
        return resp != null ? resp : "";
    }
}
