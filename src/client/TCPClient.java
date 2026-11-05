package client;

import common.Message;
import common.MessageType;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class TCPClient {
    private String host;
    private int port;
    private String username;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isConnected = false;
    private Consumer<Message> messageListener;

    public TCPClient(String host, int port, String username, Consumer<Message> messageListener) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.messageListener = messageListener;
    }

    public boolean connect(String password) {
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Send Login Request
            Message loginMsg = new Message(MessageType.LOGIN, username, "SERVER", password);
            out.writeObject(loginMsg);
            out.flush();

            // Wait for Server response
            Object responseObj = in.readObject();
            if (responseObj instanceof Message) {
                Message resp = (Message) responseObj;
                if (resp.getType() == MessageType.LOGIN_SUCCESS) {
                    isConnected = true;
                    startMessageReceiver();
                    return true;
                } else {
                    if (messageListener != null) messageListener.accept(resp);
                    close();
                    return false;
                }
            }
        } catch (Exception e) {
            System.err.println("[TCP Client] Không thể kết nối tới Server: " + e.getMessage());
            close();
        }
        return false;
    }

    private void startMessageReceiver() {
        new Thread(() -> {
            try {
                while (isConnected) {
                    Object obj = in.readObject();
                    if (obj instanceof Message) {
                        Message msg = (Message) obj;
                        if (messageListener != null) {
                            messageListener.accept(msg);
                        }
                    }
                }
            } catch (Exception e) {
                if (isConnected) {
                    Message disconnectMsg = new Message(MessageType.NOTIFICATION, "HỆ THỐNG", username, "Mất kết nối với Server.");
                    if (messageListener != null) messageListener.accept(disconnectMsg);
                }
            } finally {
                close();
            }
        }).start();
    }

    public synchronized void sendMessage(Message msg) {
        if (!isConnected || out == null) return;
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            System.err.println("[TCP Client] Lỗi khi gửi tin nhắn: " + e.getMessage());
        }
    }

    public void close() {
        isConnected = false;
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    public boolean isConnected() {
        return isConnected;
    }
}
