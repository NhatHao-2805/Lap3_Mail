package server;

import common.Message;
import common.MessageType;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {
    private Socket socket;
    private ChatServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;
    private boolean isRunning = true;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Handle handshake/login
            Object obj = in.readObject();
            if (obj instanceof Message) {
                Message loginMsg = (Message) obj;
                if (loginMsg.getType() == MessageType.LOGIN) {
                    this.username = loginMsg.getSender();
                    String password = loginMsg.getContent();

                    boolean authOk = server.getDbManager().registerOrLoginUser(username, password);
                    if (!authOk) {
                        sendMessage(new Message(MessageType.LOGIN_FAIL, "SERVER", username, "Sai mật khẩu!"));
                        closeConnection();
                        return;
                    }

                    if (server.isUsernameTaken(username)) {
                        sendMessage(new Message(MessageType.LOGIN_FAIL, "SERVER", username, "Tên tài khoản đang được đăng nhập ở nơi khác!"));
                        closeConnection();
                        return;
                    }

                    // Register client
                    server.registerClient(username, this);
                    sendMessage(new Message(MessageType.LOGIN_SUCCESS, "SERVER", username, "Đăng nhập thành công!"));

                    // Send recent chat history
                    List<Message> history = server.getDbManager().getMessageHistory(30);
                    for (Message histMsg : history) {
                        sendMessage(histMsg);
                    }

                    // Broadcast new user online
                    server.broadcastSystemMessage(username + " đã tham gia phòng chat.");
                    server.broadcastUserList();
                }
            }

            // Message processing loop
            while (isRunning) {
                try {
                    Object msgObj = in.readObject();
                    if (msgObj instanceof Message) {
                        Message msg = (Message) msgObj;
                        processMessage(msg);
                    }
                } catch (EOFException | java.net.SocketException e) {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[Server] Lỗi kết nối với client " + username + ": " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void processMessage(Message msg) {
        switch (msg.getType()) {
            case PUBLIC_CHAT:
                server.getDbManager().saveMessage(msg);
                server.broadcastMessage(msg);
                break;

            case PRIVATE_CHAT:
                server.getDbManager().saveMessage(msg);
                server.sendPrivateMessage(msg);
                break;

            case FILE_TRANSFER:
            case AUDIO_VOICE:
            case VIDEO_FILE:
                server.getDbManager().saveMessage(msg);
                if ("ALL".equalsIgnoreCase(msg.getReceiver())) {
                    server.broadcastMessage(msg);
                } else {
                    server.sendPrivateMessage(msg);
                }
                break;

            case LOGOUT:
                isRunning = false;
                break;

            default:
                break;
        }
    }

    public synchronized void sendMessage(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            System.err.println("[Server] Lỗi gửi tin nhắn cho " + username + ": " + e.getMessage());
        }
    }

    public void closeConnection() {
        isRunning = false;
        if (username != null) {
            server.removeClient(username);
            server.broadcastSystemMessage(username + " đã rời phòng chat.");
            server.broadcastUserList();
        }
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    public String getUsername() {
        return username;
    }
}
