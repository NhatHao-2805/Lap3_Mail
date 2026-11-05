package client;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class POP3Client {
    private Consumer<String> logListener;

    public static class Pop3Message {
        public int id;
        public String from = "";
        public String to = "";
        public String subject = "";
        public String date = "";
        public String body = "";

        public Pop3Message(int id) {
            this.id = id;
        }
    }

    public POP3Client(Consumer<String> logListener) {
        this.logListener = logListener;
    }

    private void log(String s) {
        if (logListener != null) logListener.accept(s);
    }

    public List<Pop3Message> fetchInbox(String host, int port, String userEmail, String password) {
        List<Pop3Message> emails = new ArrayList<>();
        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8))) {

            socket.setSoTimeout(10000);
            String welcome = reader.readLine();
            log("POP3 S: " + welcome);
            if (!welcome.startsWith("+OK")) return emails;

            if (!sendCheck(writer, reader, "USER " + userEmail)) return emails;
            if (!sendCheck(writer, reader, "PASS " + password)) return emails;

            // STAT
            writer.write("STAT\r\n");
            writer.flush();
            String statResp = reader.readLine();
            log("POP3 S: " + statResp);
            if (!statResp.startsWith("+OK")) return emails;

            String[] statTokens = statResp.split(" ");
            int count = Integer.parseInt(statTokens[1]);

            // Fetch each message via RETR
            for (int i = 1; i <= count; i++) {
                writer.write("RETR " + i + "\r\n");
                writer.flush();
                String retrResp = reader.readLine();
                log("POP3 S: " + retrResp);
                if (retrResp.startsWith("+OK")) {
                    Pop3Message msg = new Pop3Message(i);
                    boolean inHeaders = true;
                    StringBuilder body = new StringBuilder();

                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (".".equals(line)) break;
                        if (inHeaders) {
                            if (line.isEmpty()) {
                                inHeaders = false;
                            } else {
                                String lower = line.toLowerCase();
                                if (lower.startsWith("from:")) msg.from = line.substring(5).trim();
                                else if (lower.startsWith("to:")) msg.to = line.substring(3).trim();
                                else if (lower.startsWith("subject:")) msg.subject = line.substring(8).trim();
                                else if (lower.startsWith("date:")) msg.date = line.substring(5).trim();
                            }
                        } else {
                            body.append(line).append("\n");
                        }
                    }
                    msg.body = body.toString();
                    emails.add(msg);
                }
            }

            writer.write("QUIT\r\n");
            writer.flush();
            reader.readLine();
        } catch (Exception e) {
            log("[POP3 Lỗi] " + e.getMessage());
        }
        return emails;
    }

    private boolean sendCheck(BufferedWriter writer, BufferedReader reader, String cmd) throws IOException {
        log("POP3 C: " + (cmd.startsWith("PASS ") ? "PASS ******" : cmd));
        writer.write(cmd + "\r\n");
        writer.flush();
        String resp = reader.readLine();
        log("POP3 S: " + resp);
        return resp != null && resp.startsWith("+OK");
    }
}
