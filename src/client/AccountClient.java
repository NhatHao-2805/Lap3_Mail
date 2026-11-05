package client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Account registration is a custom extension of the local POP3 server. */
public final class AccountClient {
    public static void execute(String host, int port, String email, String password, String name) throws IOException {
        if (email.contains("\r") || email.contains("\n") || password.contains("\r") || password.contains("\n"))
            throw new IOException("Thông tin chứa ký tự không hợp lệ.");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(10000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            check(reader);
            if (name != null) {
                send(writer, reader, "REGISTER " + encode(email) + " " + encode(password) + " " + encode(name));
            } else {
                send(writer, reader, "USER " + email);
                send(writer, reader, "PASS " + password);
            }
            writer.write("QUIT\r\n"); writer.flush();
        }
    }
    private static String encode(String value) { return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static void send(BufferedWriter writer, BufferedReader reader, String command) throws IOException {
        writer.write(command + "\r\n"); writer.flush(); check(reader);
    }
    private static void check(BufferedReader reader) throws IOException {
        String response = reader.readLine();
        if (response == null || !response.startsWith("+OK"))
            throw new IOException(response == null ? "Server đã đóng kết nối." : response.replaceFirst("^-ERR ?", ""));
    }
}
