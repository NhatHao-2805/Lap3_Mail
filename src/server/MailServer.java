package server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import ui.MailTheme;
import static ui.MailTheme.*;
import java.io.IOException;
import java.net.*;
import java.util.Enumeration;

public class MailServer extends JFrame {
    public static final int SMTP_PORT = 2525;
    public static final int POP3_PORT = 1100;

    private MailDatabase database;
    private ServerSocket smtpServerSocket;
    private ServerSocket pop3ServerSocket;
    private boolean isRunning = false;

    // UI
    private JTextArea logArea;
    private JLabel statusLabel;
    private JButton toggleBtn;
    private JTable dbTable;
    private DefaultTableModel dbTableModel;

    public MailServer() {
        database = new MailDatabase();
        initUI();
    }

    private void initUI() {
        setTitle("VKU Mail | Trung tâm điều hành"); setSize(1120, 780);
        setMinimumSize(new Dimension(900, 650)); setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); setLocationRelativeTo(null);
        JPanel root = panel(new BorderLayout(0, 24), BG, 28);
        JPanel top = panel(new BorderLayout(), BG, 0);
        JPanel title = panel(new GridLayout(3, 1, 0, 6), BG, 0);
        title.add(label("VKU / MAIL SERVER", 11, BLUE, true));
        title.add(label("Trung tâm điều hành", 28, INK, true));
        title.add(label("Quản lý dịch vụ thư và theo dõi hoạt động trên mạng.", 13, MUTED, false)); top.add(title);
        JPanel controls = panel(new GridLayout(2, 1, 0, 12), BG, 0);
        statusLabel = label("Dịch vụ đang dừng", 12, MUTED, true); statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        toggleBtn = button("Khởi động Mail Server", true);
        toggleBtn.addActionListener(e -> { if (isRunning) stopServer(); else startServer(); });
        controls.add(statusLabel); controls.add(toggleBtn); top.add(controls, BorderLayout.EAST);
        JPanel overview = panel(new BorderLayout(0, 24), BG, 0); overview.add(top, BorderLayout.NORTH);
        JPanel cards = panel(new GridLayout(1, 3, 16, 0), BG, 0);
        cards.add(serviceCard("ĐỊA CHỈ MÁY CHỦ", getLocalIpAddress(), "Dùng IP này để kết nối từ máy khác"));
        cards.add(serviceCard("DỊCH VỤ GỬI THƯ", "SMTP / " + SMTP_PORT, "Tiếp nhận và chuyển thư nội bộ"));
        cards.add(serviceCard("DỊCH VỤ NHẬN THƯ", "POP3 / " + POP3_PORT, "Xác thực tài khoản và đọc hộp thư"));
        overview.add(cards); root.add(overview, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane(); tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        logArea = new JTextArea(); logArea.setEditable(false); logArea.setBackground(NAVY);
        logArea.setForeground(new Color(183, 210, 243)); logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setMargin(new Insets(20, 20, 20, 20));
        tabs.addTab("   Nhật ký trực tiếp   ", scroll(logArea));
        JPanel databasePanel = panel(new BorderLayout(0, 16), Color.WHITE, 18);
        dbTableModel = new DefaultTableModel(new String[]{"ID", "Người gửi", "Người nhận", "Tiêu đề", "Ngày gửi", "Kích thước"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        dbTable = new JTable(dbTableModel); table(dbTable); dbTable.setAutoCreateRowSorter(true);
        dbTable.getColumnModel().getColumn(0).setMaxWidth(55);
        dbTable.getColumnModel().getColumn(3).setPreferredWidth(260);
        JPanel databaseHeading = panel(new BorderLayout(), Color.WHITE, 0);
        databaseHeading.add(label("Dữ liệu hộp thư mẫu", 18, INK, true));
        JButton reload = button("Làm mới dữ liệu", false); reload.addActionListener(e -> reloadDatabaseEmails());
        databaseHeading.add(reload, BorderLayout.EAST); databasePanel.add(databaseHeading, BorderLayout.NORTH);
        databasePanel.add(scroll(dbTable));
        tabs.addTab("   Cơ sở dữ liệu SQLite   ", databasePanel); root.add(tabs);
        JPanel footer = panel(new BorderLayout(), BG, 0);
        footer.add(label("LẬP TRÌNH MẠNG  /  LAB 03", 10, MUTED, true));
        footer.add(label("SMTP · POP3 · SQLite", 11, MUTED, false), BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH); setContentPane(root);
    }

    private JPanel serviceCard(String title, String value, String detail) {
        JPanel card = panel(new GridLayout(3, 1, 0, 8), Color.WHITE, 20);
        card.add(label(title, 10, MUTED, true)); card.add(label(value, 22, INK, true)); card.add(label(detail, 11, MUTED, false));
        return card;
    }

    public synchronized void startServer() {
        if (isRunning) return;
        isRunning = true;

        // Start SMTP listener thread
        new Thread(() -> {
            try {
                smtpServerSocket = new ServerSocket(SMTP_PORT);
                log("[SMTP Server] Đang lắng nghe gửi thư tại cổng " + SMTP_PORT);
                while (isRunning) {
                    try {
                        Socket s = smtpServerSocket.accept();
                        new Thread(new SMTPServerHandler(s, this)).start();
                    } catch (SocketException ignored) { break; }
                }
            } catch (IOException e) {
                log("[SMTP ERROR] " + e.getMessage());
            }
        }).start();

        // Start POP3 listener thread
        new Thread(() -> {
            try {
                pop3ServerSocket = new ServerSocket(POP3_PORT);
                log("[POP3 Server] Đang lắng nghe nhận thư tại cổng " + POP3_PORT);
                while (isRunning) {
                    try {
                        Socket s = pop3ServerSocket.accept();
                        new Thread(new POP3ServerHandler(s, this)).start();
                    } catch (SocketException ignored) { break; }
                }
            } catch (IOException e) {
                log("[POP3 ERROR] " + e.getMessage());
            }
        }).start();

        statusLabel.setText("Trạng thái: Đang chạy");
        statusLabel.setForeground(new Color(16, 185, 129));
        toggleBtn.setText("Dừng Mail Server");
        toggleBtn.setBackground(new Color(239, 68, 68));
    }

    public synchronized void stopServer() {
        isRunning = false;
        try {
            if (smtpServerSocket != null && !smtpServerSocket.isClosed()) smtpServerSocket.close();
            if (pop3ServerSocket != null && !pop3ServerSocket.isClosed()) pop3ServerSocket.close();
        } catch (IOException ignored) {}

        statusLabel.setText("Trạng thái: Dừng");
        statusLabel.setForeground(new Color(220, 38, 38));
        toggleBtn.setText("Khởi động Mail Server");
        toggleBtn.setBackground(new Color(16, 185, 129));
        log("[Mail Server] Đã dừng toàn bộ dịch vụ.");
    }

    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void reloadDatabaseEmails() {
        dbTableModel.setRowCount(0);
        for (MailDatabase.EmailRecord r : database.getInbox("dat@vku.udn.vn")) {
            dbTableModel.addRow(new Object[]{r.id, r.sender, r.recipient, r.subject, r.date, r.size});
        }
        for (MailDatabase.EmailRecord r : database.getInbox("friend@vku.udn.vn")) {
            dbTableModel.addRow(new Object[]{r.id, r.sender, r.recipient, r.subject, r.date, r.size});
        }
    }

    public MailDatabase getDatabase() {
        return database;
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
        MailTheme.install();
        SwingUtilities.invokeLater(() -> {
            MailServer server = new MailServer();
            server.setVisible(true);
            server.startServer();
        });
    }
}
