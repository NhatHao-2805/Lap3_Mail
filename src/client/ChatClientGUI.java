package client;

import common.Message;
import common.MessageType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatClientGUI extends JFrame {
    private String username;
    private String currentTarget = "ALL"; // "ALL", "MULTICAST", "BROADCAST", or username

    private TCPClient tcpClient;
    private MulticastHandler multicastHandler;
    private BroadcastHandler broadcastHandler;
    private AudioRecorder audioRecorder;

    // UI elements
    private JTextPane chatPane;
    private HTMLEditorKit htmlKit;
    private HTMLDocument htmlDoc;
    private JTextField inputField;
    private JButton sendBtn;
    private JButton emojiBtn;
    private JButton attachBtn;
    private JButton recordBtn;
    private JButton videoBtn;
    private JLabel currentChatTitle;
    private JLabel protocolBadge;
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    private boolean isRecordingAudio = false;

    // In-memory cache for audio voice notes: map id -> byte[]
    private static final Map<String, byte[]> audioCache = new ConcurrentHashMap<>();
    private static final Map<String, String> fileCache = new ConcurrentHashMap<>();

    public ChatClientGUI() {
        audioRecorder = new AudioRecorder();
        showLoginDialog();
    }

    private void showLoginDialog() {
        JDialog loginDialog = new JDialog((Frame) null, "Đăng nhập VKU Network Chat", true);
        loginDialog.setSize(440, 320);
        loginDialog.setLocationRelativeTo(null);
        loginDialog.setLayout(new BorderLayout(10, 10));

        JPanel formPanel = new JPanel(new GridLayout(4, 2, 8, 8));
        formPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JTextField hostField = new JTextField("127.0.0.1");
        JTextField portField = new JTextField("8888");
        JTextField userField = new JTextField("User_" + new Random().nextInt(1000));
        JPasswordField passField = new JPasswordField("123456");

        formPanel.add(new JLabel("Server IP:"));
        JPanel hostBox = new JPanel(new BorderLayout(5, 0));
        hostBox.add(hostField, BorderLayout.CENTER);
        JButton discoverBtn = new JButton("🔍 Dò LAN");
        discoverBtn.setToolTipText("Tự động tìm Server trong mạng LAN bằng UDP Broadcast");
        discoverBtn.addActionListener(e -> {
            discoverBtn.setText("Đang dò...");
            discoverBtn.setEnabled(false);
            BroadcastHandler.discoverServer(
                    ip -> SwingUtilities.invokeLater(() -> {
                        hostField.setText(ip);
                        discoverBtn.setText("Đã tìm thấy!");
                        discoverBtn.setEnabled(true);
                        JOptionPane.showMessageDialog(loginDialog, "Tìm thấy Chat Server tại IP: " + ip, "Thành công", JOptionPane.INFORMATION_MESSAGE);
                    }),
                    () -> SwingUtilities.invokeLater(() -> {
                        discoverBtn.setText("🔍 Dò LAN");
                        discoverBtn.setEnabled(true);
                        JOptionPane.showMessageDialog(loginDialog, "Không tìm thấy Server tự động. Hãy nhập IP máy bạn của bạn thủ công.", "Thông báo", JOptionPane.WARNING_MESSAGE);
                    })
            );
        });
        hostBox.add(discoverBtn, BorderLayout.EAST);
        formPanel.add(hostBox);

        formPanel.add(new JLabel("Cổng (Port):"));
        formPanel.add(portField);
        formPanel.add(new JLabel("Tên hiển thị:"));
        formPanel.add(userField);
        formPanel.add(new JLabel("Mật khẩu:"));
        formPanel.add(passField);

        JButton loginBtn = new JButton("Vào phòng Chat");
        loginBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        loginBtn.setBackground(new Color(37, 99, 235));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.addActionListener(e -> {
            String host = hostField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            String u = userField.getText().trim();
            String p = new String(passField.getPassword()).trim();

            if (u.isEmpty()) {
                JOptionPane.showMessageDialog(loginDialog, "Vui lòng nhập tên người dùng!");
                return;
            }

            this.username = u;
            tcpClient = new TCPClient(host, port, username, this::handleIncomingMessage);

            boolean connected = tcpClient.connect(p);
            if (connected) {
                initMulticastAndBroadcast();
                loginDialog.dispose();
                initMainUI();
                setVisible(true);
            } else {
                JOptionPane.showMessageDialog(loginDialog, "Không thể kết nối đến Chat Server (" + host + ":" + port + "). Vui lòng kiểm tra lại IP hoặc khởi động Server trước!", "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
            }
        });

        loginDialog.add(formPanel, BorderLayout.CENTER);
        loginDialog.add(loginBtn, BorderLayout.SOUTH);
        loginDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        loginDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        loginDialog.setVisible(true);
    }

    private void initMulticastAndBroadcast() {
        // UDP Multicast group listener
        multicastHandler = new MulticastHandler(msg -> {
            if ("MULTICAST".equals(msg.getReceiver())) {
                handleIncomingMessage(msg);
            }
        });
        multicastHandler.startListening();

        // UDP Broadcast listener
        broadcastHandler = new BroadcastHandler(msg -> {
            if ("BROADCAST".equals(msg.getReceiver())) {
                handleIncomingMessage(msg);
            }
        });
        broadcastHandler.startListening();
    }

    private void initMainUI() {
        setTitle("VKU Chat App - [" + username + "]");
        setSize(950, 680);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (tcpClient != null) {
                    tcpClient.sendMessage(new Message(MessageType.LOGOUT, username, "SERVER", "Đăng xuất"));
                    tcpClient.close();
                }
                if (multicastHandler != null) multicastHandler.stop();
                if (broadcastHandler != null) broadcastHandler.stop();
            }
        });

        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));

        // LEFT SIDEBAR
        JPanel sidebar = new JPanel(new BorderLayout(5, 5));
        sidebar.setPreferredSize(new Dimension(260, 0));
        sidebar.setBackground(new Color(241, 245, 249));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(226, 232, 240)));

        // User profile header
        JPanel profileBox = new JPanel(new BorderLayout(10, 0));
        profileBox.setBorder(new EmptyBorder(12, 12, 12, 12));
        profileBox.setBackground(new Color(30, 41, 59));
        JLabel avatar = new JLabel("👤 " + username);
        avatar.setFont(new Font("Segoe UI", Font.BOLD, 15));
        avatar.setForeground(Color.WHITE);
        JLabel status = new JLabel("● Trực tuyến");
        status.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        status.setForeground(new Color(52, 211, 153));
        profileBox.add(avatar, BorderLayout.CENTER);
        profileBox.add(status, BorderLayout.SOUTH);
        sidebar.add(profileBox, BorderLayout.NORTH);

        // Channels & Users List
        userListModel = new DefaultListModel<>();
        userListModel.addElement("🌐 Phòng công cộng (TCP Public)");
        userListModel.addElement("👥 Multicast Group [230.0.0.1]");
        userListModel.addElement("📢 Broadcast Toàn LAN [UDP]");

        userList = new JList<>(userListModel);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setSelectedIndex(0);
        userList.setBackground(new Color(248, 250, 252));
        userList.setFixedCellHeight(38);

        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = userList.getSelectedValue();
                if (selected == null) return;
                if (selected.contains("Phòng công cộng")) {
                    currentTarget = "ALL";
                    currentChatTitle.setText("🌐 Phòng chat công cộng (TCP)");
                    protocolBadge.setText("Giao thức: TCP Socket (Độ tin cậy cao)");
                    protocolBadge.setForeground(new Color(37, 99, 235));
                } else if (selected.contains("Multicast Group")) {
                    currentTarget = "MULTICAST";
                    currentChatTitle.setText("👥 Nhóm Multicast [230.0.0.1:4446]");
                    protocolBadge.setText("Giao thức: UDP MulticastSocket (Nhóm LAN)");
                    protocolBadge.setForeground(new Color(124, 58, 237));
                } else if (selected.contains("Broadcast Toàn LAN")) {
                    currentTarget = "BROADCAST";
                    currentChatTitle.setText("📢 Kênh Broadcast Toàn Mạng LAN [Port 8890]");
                    protocolBadge.setText("Giao thức: UDP Datagram Broadcast");
                    protocolBadge.setForeground(new Color(217, 119, 6));
                } else {
                    String targetUser = selected.replace("👤 ", "").trim();
                    currentTarget = targetUser;
                    currentChatTitle.setText("👤 Nhắn riêng với: " + targetUser);
                    protocolBadge.setText("Giao thức: TCP Private Chat (P2P Client-Server)");
                    protocolBadge.setForeground(new Color(5, 150, 105));
                }
            }
        });

        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setBorder(BorderFactory.createTitledBorder("Kênh & Bạn bè"));
        sidebar.add(userScroll, BorderLayout.CENTER);
        mainPanel.add(sidebar, BorderLayout.WEST);

        // CENTER CHAT AREA
        JPanel chatPanel = new JPanel(new BorderLayout());

        // Header
        JPanel chatHeader = new JPanel(new BorderLayout(10, 5));
        chatHeader.setBackground(Color.WHITE);
        chatHeader.setBorder(new EmptyBorder(12, 16, 12, 16));
        currentChatTitle = new JLabel("🌐 Phòng chat công cộng (TCP)");
        currentChatTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        protocolBadge = new JLabel("Giao thức: TCP Socket (Độ tin cậy cao)");
        protocolBadge.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        protocolBadge.setForeground(new Color(37, 99, 235));

        chatHeader.add(currentChatTitle, BorderLayout.WEST);
        chatHeader.add(protocolBadge, BorderLayout.EAST);
        chatHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)));
        chatPanel.add(chatHeader, BorderLayout.NORTH);

        // Messages Box
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setContentType("text/html");
        htmlKit = new HTMLEditorKit();
        htmlDoc = new HTMLDocument();
        chatPane.setEditorKit(htmlKit);
        chatPane.setDocument(htmlDoc);
        try {
            htmlKit.insertHTML(htmlDoc, htmlDoc.getLength(),
                    "<style>body { font-family: Segoe UI, sans-serif; background-color: #f8fafc; margin: 10px; }" +
                            ".msg-row { margin-bottom: 12px; }" +
                            ".msg-me { text-align: right; }" +
                            ".msg-other { text-align: left; }" +
                            ".bubble-me { display: inline-block; background-color: #2563eb; color: #ffffff; padding: 8px 14px; border-radius: 12px; max-width: 65%; text-align: left; }" +
                            ".bubble-other { display: inline-block; background-color: #e2e8f0; color: #1e293b; padding: 8px 14px; border-radius: 12px; max-width: 65%; text-align: left; }" +
                            ".sender-name { font-size: 11px; font-weight: bold; color: #64748b; margin-bottom: 3px; }" +
                            ".timestamp { font-size: 10px; opacity: 0.7; margin-left: 8px; }" +
                            ".sys-msg { text-align: center; color: #94a3b8; font-size: 11px; margin: 8px 0; font-style: italic; }" +
                            ".tag-multicast { background: #7c3aed; color: white; padding: 2px 6px; border-radius: 4px; font-size: 10px; }" +
                            ".tag-broadcast { background: #d97706; color: white; padding: 2px 6px; border-radius: 4px; font-size: 10px; }" +
                            "</style><body></body>", 0, 0, null);
        } catch (Exception ignored) {}

        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(null);
        chatPanel.add(chatScroll, BorderLayout.CENTER);

        // Bottom Controls
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBackground(Color.WHITE);
        bottomPanel.setBorder(new EmptyBorder(8, 12, 12, 12));

        // Tool buttons bar (Emoji, Attach, Voice, Video)
        JPanel toolsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        toolsBar.setBackground(Color.WHITE);

        emojiBtn = createToolButton("😀 Emoji", "Chèn icon biểu cảm");
        emojiBtn.addActionListener(e -> showEmojiMenu(emojiBtn));

        attachBtn = createToolButton("📎 Đính kèm File", "Gửi file tài liệu, hình ảnh qua TCP");
        attachBtn.addActionListener(e -> chooseAndSendFile(false));

        recordBtn = createToolButton("🎙️ Ghi âm Voice", "Ghi âm giọng nói qua Microphone");
        recordBtn.addActionListener(e -> toggleVoiceRecording());

        videoBtn = createToolButton("🎥 Gửi Video", "Gửi video file (.mp4, .mkv, .avi)");
        videoBtn.addActionListener(e -> chooseAndSendFile(true));

        JButton playLastAudioBtn = createToolButton("▶ Nghe Voice mới nhất", "Phát âm thanh gần nhất nhận được");
        playLastAudioBtn.addActionListener(e -> {
            if (!audioCache.isEmpty()) {
                String lastKey = new ArrayList<>(audioCache.keySet()).get(audioCache.size() - 1);
                AudioRecorder.playAudio(audioCache.get(lastKey));
            } else {
                JOptionPane.showMessageDialog(this, "Chưa có file âm thanh/voice note nào trong phiên này.");
            }
        });

        toolsBar.add(emojiBtn);
        toolsBar.add(attachBtn);
        toolsBar.add(recordBtn);
        toolsBar.add(videoBtn);
        toolsBar.add(playLastAudioBtn);
        bottomPanel.add(toolsBar, BorderLayout.NORTH);

        // Input field and send button
        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
        inputBar.setOpaque(false);
        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        inputField.addActionListener(e -> sendTextMessage());

        sendBtn = new JButton("Gửi tin nhắn");
        sendBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sendBtn.setBackground(new Color(37, 99, 235));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setFocusPainted(false);
        sendBtn.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        sendBtn.addActionListener(e -> sendTextMessage());

        inputBar.add(inputField, BorderLayout.CENTER);
        inputBar.add(sendBtn, BorderLayout.EAST);
        bottomPanel.add(inputBar, BorderLayout.CENTER);

        chatPanel.add(bottomPanel, BorderLayout.SOUTH);
        mainPanel.add(chatPanel, BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    private JButton createToolButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setToolTipText(tooltip);
        btn.setBackground(new Color(241, 245, 249));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1, true),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        btn.setFocusPainted(false);
        return btn;
    }

    private void showEmojiMenu(Component parent) {
        JPopupMenu popup = new JPopupMenu();
        JPanel emojiPanel = new JPanel(new GridLayout(4, 5, 4, 4));
        emojiPanel.setBorder(new EmptyBorder(6, 6, 6, 6));

        String[] emojis = {
                "😀", "😂", "😍", "😎", "🤔",
                "😭", "😡", "👍", "👎", "❤️",
                "🔥", "🎉", "🚀", "💯", "☕",
                "🍕", "👏", "✨", "⭐", "🎁"
        };

        for (String emo : emojis) {
            JButton b = new JButton(emo);
            b.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
            b.setMargin(new Insets(2, 2, 2, 2));
            b.setContentAreaFilled(false);
            b.addActionListener(e -> {
                inputField.setText(inputField.getText() + emo);
                inputField.requestFocus();
                popup.setVisible(false);
            });
            emojiPanel.add(b);
        }
        popup.add(emojiPanel);
        popup.show(parent, 0, -popup.getPreferredSize().height);
    }

    private void toggleVoiceRecording() {
        if (!isRecordingAudio) {
            try {
                audioRecorder.startRecording();
                isRecordingAudio = true;
                recordBtn.setText("⏹ Dừng & Gửi Voice");
                recordBtn.setBackground(new Color(239, 68, 68));
                recordBtn.setForeground(Color.WHITE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể kích hoạt Micro: " + ex.getMessage() + "\nSẽ tự động tạo mẫu âm thanh giả lập.");
                byte[] beep = AudioRecorder.generateBeepSound();
                sendAudioMessage(beep);
            }
        } else {
            byte[] audioData = audioRecorder.stopRecording();
            isRecordingAudio = false;
            recordBtn.setText("🎙️ Ghi âm Voice");
            recordBtn.setBackground(new Color(241, 245, 249));
            recordBtn.setForeground(Color.BLACK);
            sendAudioMessage(audioData);
        }
    }

    private void sendAudioMessage(byte[] audioData) {
        if (audioData == null || audioData.length == 0) return;
        String fileName = "VoiceNote_" + System.currentTimeMillis() + ".wav";
        Message msg;
        if ("MULTICAST".equals(currentTarget)) {
            msg = new Message(MessageType.AUDIO_VOICE, username, "MULTICAST", "🎙️ Voice note âm thanh (" + (audioData.length / 1024) + " KB)", fileName, audioData);
            multicastHandler.sendMulticastMessage(msg);
            renderMessage(msg, true);
        } else if ("BROADCAST".equals(currentTarget)) {
            msg = new Message(MessageType.AUDIO_VOICE, username, "BROADCAST", "🎙️ Voice note toàn LAN (" + (audioData.length / 1024) + " KB)", fileName, audioData);
            broadcastHandler.sendBroadcastMessage(msg);
            renderMessage(msg, true);
        } else {
            msg = new Message(MessageType.AUDIO_VOICE, username, currentTarget, "🎙️ Voice note (" + (audioData.length / 1024) + " KB)", fileName, audioData);
            tcpClient.sendMessage(msg);
        }
    }

    private void chooseAndSendFile(boolean isVideo) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(isVideo ? "Chọn file Video để gửi" : "Chọn file đính kèm để gửi");
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            try {
                if (selectedFile.length() > 50 * 1024 * 1024) {
                    JOptionPane.showMessageDialog(this, "Vui lòng chọn file dưới 50MB để đảm bảo tốc độ!");
                    return;
                }
                byte[] bytes = Files.readAllBytes(selectedFile.toPath());
                MessageType mType = isVideo ? MessageType.VIDEO_FILE : MessageType.FILE_TRANSFER;
                String desc = (isVideo ? "🎥 Video clip: " : "📎 Đính kèm: ") + selectedFile.getName() + " (" + (bytes.length / 1024) + " KB)";

                Message msg = new Message(mType, username, currentTarget, desc, selectedFile.getName(), bytes);
                if ("MULTICAST".equals(currentTarget)) {
                    multicastHandler.sendMulticastMessage(msg);
                    renderMessage(msg, true);
                } else if ("BROADCAST".equals(currentTarget)) {
                    broadcastHandler.sendBroadcastMessage(msg);
                    renderMessage(msg, true);
                } else {
                    tcpClient.sendMessage(msg);
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Lỗi đọc file: " + ex.getMessage());
            }
        }
    }

    private void sendTextMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        if ("MULTICAST".equals(currentTarget)) {
            Message msg = new Message(MessageType.MULTICAST_CHAT, username, "MULTICAST", text);
            multicastHandler.sendMulticastMessage(msg);
            renderMessage(msg, true);
        } else if ("BROADCAST".equals(currentTarget)) {
            Message msg = new Message(MessageType.BROADCAST_CHAT, username, "BROADCAST", text);
            broadcastHandler.sendBroadcastMessage(msg);
            renderMessage(msg, true);
        } else {
            MessageType type = "ALL".equals(currentTarget) ? MessageType.PUBLIC_CHAT : MessageType.PRIVATE_CHAT;
            Message msg = new Message(type, username, currentTarget, text);
            tcpClient.sendMessage(msg);
        }
        inputField.setText("");
    }

    private void handleIncomingMessage(Message msg) {
        SwingUtilities.invokeLater(() -> {
            if (msg.getType() == MessageType.USER_LIST) {
                updateUserList(msg.getContent());
                return;
            }

            if (msg.getType() == MessageType.NOTIFICATION) {
                renderSystemMessage(msg.getContent());
                return;
            }

            // Save audio or file to cache/disk if attached
            if (msg.getFileData() != null) {
                if (msg.getType() == MessageType.AUDIO_VOICE) {
                    audioCache.put(msg.getFileName(), msg.getFileData());
                    AudioRecorder.playAudio(msg.getFileData()); // auto play voice
                }
                saveReceivedFile(msg.getFileName(), msg.getFileData());
            }

            boolean isMe = username.equals(msg.getSender());
            renderMessage(msg, isMe);
        });
    }

    private void saveReceivedFile(String fileName, byte[] data) {
        try {
            File dir = new File("downloads");
            if (!dir.exists()) dir.mkdirs();
            File dest = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                fos.write(data);
            }
            fileCache.put(fileName, dest.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Lỗi lưu file tải về: " + e.getMessage());
        }
    }

    private void updateUserList(String commaSeparatedUsers) {
        userListModel.clear();
        userListModel.addElement("🌐 Phòng công cộng (TCP Public)");
        userListModel.addElement("👥 Multicast Group [230.0.0.1]");
        userListModel.addElement("📢 Broadcast Toàn LAN [UDP]");

        if (commaSeparatedUsers != null && !commaSeparatedUsers.isEmpty()) {
            String[] users = commaSeparatedUsers.split(",");
            for (String u : users) {
                u = u.trim();
                if (!u.isEmpty() && !u.equals(username)) {
                    userListModel.addElement("👤 " + u);
                }
            }
        }
    }

    private void renderSystemMessage(String text) {
        String html = "<div class='sys-msg'>🔔 " + escapeHtml(text) + "</div>";
        appendHtml(html);
    }

    private void renderMessage(Message msg, boolean isMe) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='msg-row ").append(isMe ? "msg-me" : "msg-other").append("'>");

        String tag = "";
        if ("MULTICAST".equals(msg.getReceiver()) || msg.getType() == MessageType.MULTICAST_CHAT) {
            tag = " <span class='tag-multicast'>MULTICAST UDP</span>";
        } else if ("BROADCAST".equals(msg.getReceiver()) || msg.getType() == MessageType.BROADCAST_CHAT) {
            tag = " <span class='tag-broadcast'>BROADCAST UDP</span>";
        } else if (!"ALL".equals(msg.getReceiver()) && msg.getType() == MessageType.PRIVATE_CHAT) {
            tag = " <span style='background:#059669;color:white;padding:2px 6px;border-radius:4px;font-size:10px;'>PRIVATE TCP</span>";
        }

        if (!isMe) {
            sb.append("<div class='sender-name'>").append(escapeHtml(msg.getSender())).append(tag).append("</div>");
        }

        sb.append("<div class='").append(isMe ? "bubble-me" : "bubble-other").append("'>");

        // Content
        if (msg.getContent() != null) {
            sb.append(escapeHtml(msg.getContent()));
        }

        // Action links for attachments or audio
        if (msg.getFileName() != null) {
            if (msg.getType() == MessageType.AUDIO_VOICE) {
                sb.append("<br/><small>🎵 Đã nhận voice note (đang tự động phát...)</small>");
            } else if (msg.getType() == MessageType.VIDEO_FILE) {
                sb.append("<br/><small>📁 Đã lưu video vào thư mục <b>downloads/").append(escapeHtml(msg.getFileName())).append("</b></small>");
            } else {
                sb.append("<br/><small>📁 Đã tải về: <b>downloads/").append(escapeHtml(msg.getFileName())).append("</b></small>");
            }
        }

        sb.append("<span class='timestamp'>").append(msg.getTimestamp()).append("</span>");
        sb.append("</div>");
        sb.append("</div>");

        appendHtml(sb.toString());
    }

    private void appendHtml(String html) {
        try {
            htmlKit.insertHTML(htmlDoc, htmlDoc.getLength(), html, 0, 0, null);
            chatPane.setCaretPosition(chatPane.getDocument().getLength());
        } catch (Exception ignored) {}
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br/>");
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(ChatClientGUI::new);
    }
}
