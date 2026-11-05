package server;

import common.Message;
import common.MessageType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer extends JFrame {
    public static final int TCP_PORT = 8888;
    public static final int UDP_DISCOVERY_PORT = 8889;

    private Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private DatabaseManager dbManager;
    private ServerSocket serverSocket;
    private DatagramSocket udpDiscoverySocket;
    private boolean isRunning = false;

    // UI components
    private JTextArea logArea;
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    private JLabel statusLabel;
    private JLabel ipLabel;
    private JButton toggleServerBtn;

    public ChatServer() {
        dbManager = new DatabaseManager();
        initUI();
    }

    private void initUI() {
        setTitle("LTM Lab 1 - Chat Server (TCP & UDP Multi-cast/Discovery)");
        setSize(800, 520);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(new Color(245, 247, 250));

        // Top Header
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setOpaque(false);

        String localIP = getLocalIpAddress();
        ipLabel = new JLabel("Server IP LAN: " + localIP + " | TCP Port: " + TCP_PORT + " | UDP Discovery: " + UDP_DISCOVERY_PORT);
        ipLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        ipLabel.setForeground(new Color(30, 41, 59));

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        controlPanel.setOpaque(false);
        statusLabel = new JLabel("Trạng thái: Dừng");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        statusLabel.setForeground(new Color(220, 38, 38));

        toggleServerBtn = new JButton("Khởi động Server");
        toggleServerBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        toggleServerBtn.setBackground(new Color(16, 185, 129));
        toggleServerBtn.setForeground(Color.WHITE);
        toggleServerBtn.setFocusPainted(false);
        toggleServerBtn.addActionListener(e -> {
            if (!isRunning) startServer();
            else stopServer();
        });

        controlPanel.add(statusLabel);
        controlPanel.add(toggleServerBtn);
        topPanel.add(ipLabel, BorderLayout.WEST);
        topPanel.add(controlPanel, BorderLayout.EAST);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Center: Log area & Connected users list
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(255, 255, 255));
        logArea.setMargin(new Insets(8, 8, 8, 8));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Nhật ký Server (Log Activity)"));

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(220, 0));
        userScroll.setBorder(BorderFactory.createTitledBorder("Clients đang Online"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, logScroll, userScroll);
        splitPane.setResizeWeight(0.75);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public synchronized void startServer() {
        if (isRunning) return;
        isRunning = true;

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                log("[TCP Server] Đang lắng nghe kết nối tại cổng " + TCP_PORT + "...");
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Trạng thái: Đang chạy");
                    statusLabel.setForeground(new Color(16, 185, 129));
                    toggleServerBtn.setText("Dừng Server");
                    toggleServerBtn.setBackground(new Color(239, 68, 68));
                });

                // Start UDP LAN Discovery Service
                startUdpDiscoveryService();

                while (isRunning) {
                    try {
                        Socket socket = serverSocket.accept();
                        log("[TCP Connect] Client kết nối từ: " + socket.getRemoteSocketAddress());
                        ClientHandler handler = new ClientHandler(socket, this);
                        new Thread(handler).start();
                    } catch (SocketException e) {
                        break;
                    }
                }
            } catch (IOException e) {
                log("[ERROR] Không thể khởi động TCP Server: " + e.getMessage());
                stopServer();
            }
        }).start();
    }

    private void startUdpDiscoveryService() {
        new Thread(() -> {
            try {
                udpDiscoverySocket = new DatagramSocket(UDP_DISCOVERY_PORT);
                byte[] buffer = new byte[1024];
                log("[UDP Discovery] Đã kích hoạt lắng nghe dò tìm máy trong LAN trên cổng " + UDP_DISCOVERY_PORT);

                while (isRunning) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpDiscoverySocket.receive(packet);
                    String request = new String(packet.getData(), 0, packet.getLength()).trim();
                    if ("DISCOVER_CHAT_SERVER".equals(request)) {
                        String reply = "CHAT_SERVER_OFFER:" + TCP_PORT;
                        byte[] replyBytes = reply.getBytes();
                        DatagramPacket responsePacket = new DatagramPacket(
                                replyBytes, replyBytes.length, packet.getAddress(), packet.getPort()
                        );
                        udpDiscoverySocket.send(responsePacket);
                        log("[UDP Discovery] Đã phản hồi vị trí Server tới: " + packet.getAddress().getHostAddress());
                    }
                }
            } catch (Exception e) {
                if (isRunning) log("[UDP Discovery] Ngoại lệ: " + e.getMessage());
            }
        }).start();
    }

    public synchronized void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            if (udpDiscoverySocket != null && !udpDiscoverySocket.isClosed()) udpDiscoverySocket.close();
            for (ClientHandler client : clients.values()) {
                client.closeConnection();
            }
            clients.clear();
            SwingUtilities.invokeLater(() -> {
                userListModel.clear();
                statusLabel.setText("Trạng thái: Dừng");
                statusLabel.setForeground(new Color(220, 38, 38));
                toggleServerBtn.setText("Khởi động Server");
                toggleServerBtn.setBackground(new Color(16, 185, 129));
            });
            log("[Server] Đã dừng toàn bộ dịch vụ.");
        } catch (IOException e) {
            log("[ERROR] Lỗi khi dừng server: " + e.getMessage());
        }
    }

    public synchronized boolean isUsernameTaken(String username) {
        return clients.containsKey(username);
    }

    public synchronized void registerClient(String username, ClientHandler handler) {
        clients.put(username, handler);
        SwingUtilities.invokeLater(() -> userListModel.addElement(username));
        log("[Online] Người dùng '" + username + "' đã kết nối.");
    }

    public synchronized void removeClient(String username) {
        clients.remove(username);
        SwingUtilities.invokeLater(() -> userListModel.removeElement(username));
        log("[Offline] Người dùng '" + username + "' đã ngắt kết nối.");
    }

    public synchronized void broadcastMessage(Message msg) {
        for (ClientHandler client : clients.values()) {
            client.sendMessage(msg);
        }
        log("[" + msg.getType() + "] " + msg.getSender() + " -> ALL: " + (msg.getContent() != null ? msg.getContent() : msg.getFileName()));
    }

    public synchronized void sendPrivateMessage(Message msg) {
        ClientHandler target = clients.get(msg.getReceiver());
        if (target != null) {
            target.sendMessage(msg);
            // send copy back to sender if different
            ClientHandler sender = clients.get(msg.getSender());
            if (sender != null && !msg.getSender().equals(msg.getReceiver())) {
                sender.sendMessage(msg);
            }
            log("[Private] " + msg.getSender() + " -> " + msg.getReceiver() + ": " + (msg.getContent() != null ? msg.getContent() : msg.getFileName()));
        } else {
            ClientHandler sender = clients.get(msg.getSender());
            if (sender != null) {
                sender.sendMessage(new Message(MessageType.NOTIFICATION, "SERVER", msg.getSender(), "Người dùng " + msg.getReceiver() + " không trực tuyến."));
            }
        }
    }

    public synchronized void broadcastSystemMessage(String text) {
        Message sysMsg = new Message(MessageType.NOTIFICATION, "HỆ THỐNG", "ALL", text);
        for (ClientHandler client : clients.values()) {
            client.sendMessage(sysMsg);
        }
    }

    public synchronized void broadcastUserList() {
        String usersStr = String.join(",", clients.keySet());
        Message listMsg = new Message(MessageType.USER_LIST, "SERVER", "ALL", usersStr);
        for (ClientHandler client : clients.values()) {
            client.sendMessage(listMsg);
        }
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> {
            ChatServer server = new ChatServer();
            server.setVisible(true);
            server.startServer();
        });
    }
}
