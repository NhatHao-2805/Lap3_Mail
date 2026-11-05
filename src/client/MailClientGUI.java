package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import ui.MailTheme;
import static ui.MailTheme.*;
import javax.swing.table.TableRowSorter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.ArrayList;
import java.util.List;

public class MailClientGUI extends JFrame {
    private String serverHost = "127.0.0.1";
    private int smtpPort = 2525;
    private int pop3Port = 1100;
    private String myEmail = "";
    private String myPassword = "";

    private SMTPClient smtpClient;
    private POP3Client pop3Client;

    // UI
    private JTable mailTable;
    private JLabel inboxCount, inboxStatus;
    private JTextField searchField;
    private TableRowSorter<DefaultTableModel> sorter;
    private JButton refreshButton;
    private long sessionVersion;

    private DefaultTableModel mailTableModel;
    private JTextArea mailPreviewArea;
    private JLabel previewSubject;
    private JLabel previewFrom;
    private JLabel previewDate;
    private JTextArea logArea;
    private List<POP3Client.Pop3Message> currentEmails = new ArrayList<>();

    public MailClientGUI() {
        smtpClient = new SMTPClient(this::appendLog);
        pop3Client = new POP3Client(this::appendLog);
        initUI();
        showLoginDialog();
    }

    private void initUI() {
        setTitle("VKU Mail | " + (myEmail.isEmpty() ? "Không gian thư của bạn" : myEmail));
        setSize(1220, 820); setMinimumSize(new Dimension(960, 680));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); setLocationRelativeTo(null);
        JPanel root = panel(new BorderLayout(), BG, 0);
        JPanel sidebar = panel(new BorderLayout(0, 24), NAVY, 22);
        sidebar.setPreferredSize(new Dimension(236, 0));
        JPanel sideTop = new JPanel(); sideTop.setOpaque(false); sideTop.setLayout(new BoxLayout(sideTop, BoxLayout.Y_AXIS));
        JLabel logo = label("VKU / MAIL", 24, Color.WHITE, true);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT); sideTop.add(logo);
        sideTop.add(Box.createVerticalStrut(6));
        sideTop.add(label("KHÔNG GIAN KẾT NỐI", 10, new Color(139, 160, 197), true));
        sideTop.add(Box.createVerticalStrut(36));
        JButton compose = button("+   Soạn thư mới", true); compose.setAlignmentX(Component.LEFT_ALIGNMENT);
        compose.setMaximumSize(new Dimension(210, 48)); compose.addActionListener(e -> showComposeDialog());
        sideTop.add(compose); sideTop.add(Box.createVerticalStrut(32));
        sideTop.add(label("HỘP THƯ CỦA BẠN", 10, new Color(139, 160, 197), true));
        sideTop.add(Box.createVerticalStrut(12));
        JButton inbox = button("Hộp thư đến", false); inbox.setBackground(new Color(39, 60, 99)); inbox.setForeground(Color.WHITE);
        inbox.setBorder(new EmptyBorder(13, 16, 13, 16)); inbox.setHorizontalAlignment(SwingConstants.LEFT);
        inbox.setAlignmentX(Component.LEFT_ALIGNMENT); inbox.setMaximumSize(new Dimension(210, 46));
        inbox.addActionListener(e -> { searchField.setText(""); refreshInbox(); }); sideTop.add(inbox);
        sidebar.add(sideTop, BorderLayout.NORTH);
        JPanel sideBottom = new JPanel(); sideBottom.setOpaque(false); sideBottom.setLayout(new BoxLayout(sideBottom, BoxLayout.Y_AXIS));
        sideBottom.add(label("TÀI KHOẢN", 10, new Color(139, 160, 197), true)); sideBottom.add(Box.createVerticalStrut(10));
        JLabel account = label(myEmail, 12, Color.WHITE, true); account.setToolTipText(myEmail); sideBottom.add(account);
        sideBottom.add(Box.createVerticalStrut(16));
        JButton logout = button("Đăng xuất / Đổi tài khoản", false);
        logout.setFont(new Font("Segoe UI", Font.PLAIN, 11)); logout.setAlignmentX(Component.LEFT_ALIGNMENT);
        logout.addActionListener(e -> {
            sessionVersion++; myPassword = ""; currentEmails = new ArrayList<>();
            mailTableModel.setRowCount(0); mailPreviewArea.setText(""); logArea.setText("");
            setVisible(false); showLoginDialog();
        }); sideBottom.add(logout); sideBottom.add(Box.createVerticalStrut(24));
        sideBottom.add(label("VKU UNIVERSITY  /  LAB 03", 10, new Color(139, 160, 197), false));
        sidebar.add(sideBottom, BorderLayout.SOUTH); root.add(sidebar, BorderLayout.WEST);

        JPanel workspace = panel(new BorderLayout(0, 18), BG, 24);
        JPanel heading = panel(new BorderLayout(20, 0), BG, 0);
        JPanel titles = panel(new GridLayout(2, 1, 0, 5), BG, 0);
        titles.add(label("Hộp thư đến", 28, INK, true));
        inboxCount = label("Không gian cho những cuộc trò chuyện của bạn", 12, MUTED, false); titles.add(inboxCount);
        heading.add(titles, BorderLayout.WEST);
        JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 8, 8), BG, 0);
        JButton trace = button("Nhật ký giao thức", false);
        refreshButton = button("Làm mới hộp thư", true); refreshButton.addActionListener(e -> refreshInbox());
        actions.add(trace); actions.add(refreshButton); heading.add(actions, BorderLayout.EAST);
        workspace.add(heading, BorderLayout.NORTH);
        JPanel center = panel(new BorderLayout(0, 0), Color.WHITE, 0);
        JPanel searchBar = panel(new BorderLayout(16, 0), Color.WHITE, 16);
        searchField = new JTextField(); input(searchField); searchField.setToolTipText("Tìm theo người gửi, tiêu đề hoặc ngày gửi");
        searchBar.add(label("Tìm thư", 13, MUTED, true), BorderLayout.WEST); searchBar.add(searchField);
        center.add(searchBar, BorderLayout.NORTH);
        mailTableModel = new DefaultTableModel(new String[]{"#", "Người gửi", "Tiêu đề", "Ngày gửi"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        mailTable = new JTable(mailTableModel); table(mailTable);
        mailTable.getColumnModel().getColumn(0).setMaxWidth(55);
        mailTable.getColumnModel().getColumn(1).setPreferredWidth(210);
        mailTable.getColumnModel().getColumn(2).setPreferredWidth(330);
        mailTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        sorter = new TableRowSorter<>(mailTableModel); mailTable.setRowSorter(sorter);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }
            private void filter() {
                String q = searchField.getText().trim();
                sorter.setRowFilter(q.isEmpty() ? null : RowFilter.regexFilter("(?iu)" + java.util.regex.Pattern.quote(q), 1, 2, 3));
                mailTable.clearSelection(); clearPreview();
                if (mailTable.getRowCount() > 0) mailTable.setRowSelectionInterval(0, 0);
                inboxCount.setText(mailTable.getRowCount() + " thư hiển thị / " + currentEmails.size() + " thư trong hộp thư");
            }
        });
        mailTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && mailTable.getSelectedRow() >= 0) {
                int index = mailTable.convertRowIndexToModel(mailTable.getSelectedRow());
                if (index < currentEmails.size()) displayEmailPreview(currentEmails.get(index));
            }
        });
        JPanel reader = panel(new BorderLayout(0, 20), Color.WHITE, 24);
        reader.setMinimumSize(new Dimension(0, 180));
        JPanel headers = panel(new GridLayout(0, 1), Color.WHITE, 0);
        headers.setLayout(new BoxLayout(headers, BoxLayout.Y_AXIS));
        headers.add(label("NỘI DUNG THƯ", 10, BLUE, true));
        previewSubject = label("Chọn một lá thư để bắt đầu", 22, INK, true);
        previewFrom = label("Người gửi và người nhận sẽ hiển thị tại đây", 12, MUTED, false);
        previewDate = label("", 11, MUTED, false);
        headers.add(Box.createVerticalStrut(8)); headers.add(previewSubject);
        headers.add(Box.createVerticalStrut(8)); headers.add(previewFrom);
        headers.add(Box.createVerticalStrut(4)); headers.add(previewDate); reader.add(headers, BorderLayout.NORTH);
        mailPreviewArea = new JTextArea(); mailPreviewArea.setEditable(false);
        mailPreviewArea.setFont(new Font("Segoe UI", Font.PLAIN, 15)); mailPreviewArea.setForeground(INK);
        mailPreviewArea.setLineWrap(true); mailPreviewArea.setWrapStyleWord(true);
        reader.add(scroll(mailPreviewArea));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll(mailTable), reader);
        split.setBorder(new EmptyBorder(0, 0, 0, 0)); split.setDividerSize(5); split.setResizeWeight(0.46);
        split.setDividerLocation(230); center.add(split);
        workspace.add(center);
        JPanel bottom = panel(new BorderLayout(0, 8), BG, 0);
        logArea = new JTextArea(6, 60); logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 11)); logArea.setBackground(NAVY); logArea.setForeground(new Color(183, 210, 243));
        logArea.setMargin(new Insets(12, 16, 12, 16));
        JScrollPane logScroll = scroll(logArea); logScroll.setVisible(false);
        trace.addActionListener(e -> { logScroll.setVisible(!logScroll.isVisible()); workspace.revalidate(); });
        bottom.add(logScroll);
        JPanel footer = panel(new BorderLayout(), BG, 0);
        inboxStatus = label("Sẵn sàng", 11, MUTED, false); footer.add(inboxStatus, BorderLayout.WEST);
        footer.add(label("Server " + serverHost + "   ·   SMTP " + smtpPort + " / POP3 " + pop3Port, 11, MUTED, false), BorderLayout.EAST);
        bottom.add(footer, BorderLayout.SOUTH); workspace.add(bottom, BorderLayout.SOUTH);
        root.add(workspace); setContentPane(root); revalidate(); repaint();
    }

    private void clearPreview() {
        previewSubject.setText(currentEmails.isEmpty() ? "Hộp thư đang trống" : "Chọn một lá thư để đọc");
        previewFrom.setText(currentEmails.isEmpty() ? "Những cuộc trò chuyện mới sẽ xuất hiện ở đây." : "");
        previewDate.setText(""); mailPreviewArea.setText("");
    }

    private void displayEmailPreview(POP3Client.Pop3Message msg) {
        previewSubject.setText(msg.subject.isEmpty() ? "(Không có tiêu đề)" : msg.subject);
        previewFrom.setText("Từ: " + msg.from + "  |  Tới: " + msg.to);
        previewDate.setText("Ngày: " + msg.date);
        mailPreviewArea.setText(msg.body);
        mailPreviewArea.setCaretPosition(0);
        previewSubject.setToolTipText(msg.subject);
        previewFrom.setToolTipText(msg.from + " → " + msg.to);
    }

    private void refreshInbox() {
        final String account = myEmail, password = myPassword, host = serverHost;
        final int port = pop3Port;
        final long version = sessionVersion;
        refreshButton.setEnabled(false); inboxStatus.setText("Đang tải thư từ server...");
        new Thread(() -> {
            appendLog("[Client] Đang lấy danh sách thư qua POP3...");
            List<POP3Client.Pop3Message> fetched = pop3Client.fetchInbox(host, port, account, password);
            SwingUtilities.invokeLater(() -> {
                if (version != sessionVersion || !account.equals(myEmail) || !password.equals(myPassword) || !host.equals(serverHost) || port != pop3Port) return;
                refreshButton.setEnabled(true);
                inboxStatus.setText("Đã hoàn tất yêu cầu lúc " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                currentEmails = fetched;
                mailTableModel.setRowCount(0);
                for (POP3Client.Pop3Message m : currentEmails) {
                    mailTableModel.addRow(new Object[]{m.id, m.from, m.subject, m.date});
                }
                inboxCount.setText(mailTable.getRowCount() + " thư hiển thị / " + currentEmails.size() + " thư trong hộp thư");
                mailTable.clearSelection(); clearPreview();
                if (mailTable.getRowCount() > 0) mailTable.setRowSelectionInterval(0, 0);
            });
        }).start();
    }

    private void showComposeDialog() {
        JDialog dialog = new JDialog(this, "VKU Mail | Soạn thư mới", true);
        dialog.setSize(720, 720); dialog.setMinimumSize(new Dimension(600, 600));
        dialog.setLocationRelativeTo(this);
        JPanel root = panel(new BorderLayout(0, 20), BG, 24);
        JPanel title = panel(new GridLayout(2, 1, 0, 6), BG, 0);
        title.add(label("Một cuộc trò chuyện mới", 25, INK, true));
        title.add(label("Viết điều bạn muốn chia sẻ. Chúng tôi sẽ chuyển lời.", 13, MUTED, false));
        root.add(title, BorderLayout.NORTH);
        JPanel editor = panel(new BorderLayout(0, 18), Color.WHITE, 20);
        JPanel fields = panel(new GridLayout(0, 1, 0, 12), Color.WHITE, 0);
        JComboBox<String> mode = new JComboBox<>(new String[]{"Mail Server nội bộ (Localhost / LAN)", "Gmail (SSL)"});
        mode.setBackground(Color.WHITE); mode.setPreferredSize(new Dimension(0, 36));
        JTextField from = new JTextField(myEmail), to = new JTextField(), subject = new JTextField();
        fields.add(field("Phương thức gửi", mode));
        JPanel addresses = panel(new GridLayout(1, 2, 14, 0), Color.WHITE, 0);
        addresses.add(field("Từ", from)); addresses.add(field("Đến", to)); fields.add(addresses);
        fields.add(field("Tiêu đề", subject)); editor.add(fields, BorderLayout.NORTH);
        JTextArea body = new JTextArea(); body.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        body.setForeground(INK); body.setLineWrap(true); body.setWrapStyleWord(true);
        body.setMargin(new Insets(14, 14, 14, 14));
        JScrollPane bodyScroll = scroll(body); bodyScroll.setBorder(BorderFactory.createLineBorder(LINE));
        editor.add(field("Nội dung thư", bodyScroll)); root.add(editor);
        JPanel footer = panel(new BorderLayout(), BG, 0);
        JLabel status = label("Sẵn sàng gửi thư", 12, MUTED, false); footer.add(status, BorderLayout.WEST);
        JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 8, 0), BG, 0);
        JButton cancel = button("Hủy", false), send = button("Gửi thư  →", true);
        cancel.addActionListener(e -> dialog.dispose()); actions.add(cancel); actions.add(send); footer.add(actions, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH); dialog.setContentPane(root);
        send.addActionListener(e -> {
            final String recipient = to.getText().trim(), sender = from.getText().trim(), subj = subject.getText().trim(), text = body.getText();
            if (!recipient.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") || sender.isEmpty() || subj.contains("\r") || subj.contains("\n")) {
                JOptionPane.showMessageDialog(dialog, "Vui lòng nhập địa chỉ gửi, nhận hợp lệ và tiêu đề trên một dòng."); return;
            }
            final boolean gmail = mode.getSelectedIndex() == 1;
            String gmailAddress = "", gmailPassword = "";
            if (gmail) {
                JTextField user = new JTextField(); JPasswordField password = new JPasswordField();
                JPanel credentials = panel(new GridLayout(0, 1, 0, 12), Color.WHITE, 12);
                credentials.add(field("Địa chỉ Gmail", user)); credentials.add(field("Google App Password", password));
                if (JOptionPane.showConfirmDialog(dialog, credentials, "Kết nối Gmail", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
                gmailAddress = user.getText().trim(); gmailPassword = new String(password.getPassword()).trim();
                if (gmailAddress.isEmpty() || gmailPassword.isEmpty()) return;
            }
            final String gu = gmailAddress, gp = gmailPassword, host = serverHost;
            final int port = smtpPort; final long version = sessionVersion;
            send.setEnabled(false); cancel.setEnabled(false); mode.setEnabled(false);
            from.setEnabled(false); to.setEnabled(false); subject.setEnabled(false); body.setEditable(false);
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE); status.setText("Đang gửi thư...");
            new SwingWorker<Boolean, Void>() {
                protected Boolean doInBackground() {
                    return gmail ? smtpClient.sendGmailSSL(gu, gp, recipient, subj, text)
                            : smtpClient.sendLocalEmail(host, port, sender, recipient, subj, text);
                }
                protected void done() {
                    send.setEnabled(true); cancel.setEnabled(true); mode.setEnabled(true);
                    from.setEnabled(true); to.setEnabled(true); subject.setEnabled(true); body.setEditable(true);
                    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                    try {
                        if (get()) {
                            dialog.dispose();
                            JOptionPane.showMessageDialog(MailClientGUI.this, "Thư của bạn đã được gửi thành công.", "Đã gửi thư", JOptionPane.INFORMATION_MESSAGE);
                            if (version == sessionVersion) refreshInbox();
                        } else status.setText("Gửi thất bại. Mở nhật ký để xem chi tiết.");
                    } catch (Exception ex) { status.setText("Không thể gửi thư. Vui lòng thử lại."); }
                }
            }.execute();
        });
        dialog.setVisible(true);
    }

    private void showLoginDialog() {
        JDialog d = new JDialog(this, "VKU Mail - Đăng nhập / Đăng ký", true);
        d.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { d.dispose(); dispose(); }
        });
        JPanel shell = panel(new BorderLayout(), BG, 0);
        shell.add(brand(), BorderLayout.WEST);
        JPanel form = panel(new BorderLayout(0, 18), Color.WHITE, 32);
        JPanel content = panel(new GridLayout(0, 1, 0, 14), Color.WHITE, 0);
        JPanel intro = panel(new GridLayout(0, 1, 0, 8), Color.WHITE, 0);
        JLabel welcome = label("Chào mừng trở lại", 29, INK, true);
        intro.add(label("TÀI KHOẢN VKU MAIL", 10, BLUE, true)); intro.add(welcome);
        JLabel introHint = label("Đăng nhập để tiếp tục cuộc trò chuyện của bạn.", 13, MUTED, false);
        intro.add(introHint);
        form.add(intro, BorderLayout.NORTH);
        JTextField host = new JTextField(serverHost);
        JTextField smtp = new JTextField(String.valueOf(smtpPort));
        JTextField pop = new JTextField(String.valueOf(pop3Port));
        JTextField email = new JTextField(myEmail);
        JPasswordField pass = new JPasswordField();
        JPasswordField confirm = new JPasswordField();
        JTextField name = new JTextField();
        JCheckBox register = new JCheckBox("Tạo tài khoản mới");
        JLabel status = label("Nhập tài khoản để mở hộp thư.", 12, MUTED, false);
        JButton submit = button("Đăng nhập  →", true);
        JPanel connection = panel(new GridLayout(1, 3, 10, 0), Color.WHITE, 0);
        connection.add(field("Server Host / IP", host)); connection.add(field("SMTP Port", smtp)); connection.add(field("POP3 Port", pop));
        content.add(connection);
        content.add(field("Địa chỉ email", email)); content.add(field("Mật khẩu", pass));
        JPanel extra = panel(new GridLayout(1, 2, 12, 0), Color.WHITE, 0);
        extra.add(field("Họ và tên", name)); extra.add(field("Nhập lại mật khẩu", confirm));
        name.setEnabled(false); confirm.setEnabled(false); extra.setVisible(false);
        JPanel options = panel(new BorderLayout(), Color.WHITE, 0);
        register.setOpaque(false); register.setForeground(BLUE); register.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JCheckBox reveal = new JCheckBox("Hiện mật khẩu"); reveal.setOpaque(false); reveal.setForeground(MUTED);
        final char echo = pass.getEchoChar();
        reveal.addActionListener(e -> { pass.setEchoChar(reveal.isSelected() ? (char) 0 : echo); confirm.setEchoChar(reveal.isSelected() ? (char) 0 : echo); });
        options.add(register, BorderLayout.WEST); options.add(reveal, BorderLayout.EAST);
        JPanel middle = panel(new BorderLayout(0, 10), Color.WHITE, 0);
        JPanel inputs = panel(new BorderLayout(0, 12), Color.WHITE, 0);
        inputs.add(content, BorderLayout.NORTH); inputs.add(extra, BorderLayout.SOUTH);
        middle.add(inputs, BorderLayout.NORTH); middle.add(options, BorderLayout.SOUTH);
        form.add(middle);
        JPanel bottom = panel(new BorderLayout(0, 12), Color.WHITE, 0);
        bottom.add(submit, BorderLayout.NORTH);
        status.setPreferredSize(new Dimension(420, 45)); bottom.add(status);
        bottom.add(label("Cùng máy: 127.0.0.1   ·   Khác máy: IP LAN của server", 11, MUTED, false), BorderLayout.SOUTH);
        form.add(bottom, BorderLayout.SOUTH);
        register.addActionListener(e -> {
            boolean creating = register.isSelected();
            name.setEnabled(creating); confirm.setEnabled(creating); extra.setVisible(creating);
            welcome.setText(creating ? "Tạo kết nối đầu tiên" : "Chào mừng trở lại");
            introHint.setText(creating ? "Tạo tài khoản để bắt đầu gửi và nhận thư." : "Đăng nhập để tiếp tục cuộc trò chuyện của bạn.");
            submit.setText(creating ? "Tạo tài khoản  →" : "Đăng nhập  →");
            status.setText(creating ? "Mật khẩu 6–128 ký tự. Hãy xác nhận lại mật khẩu." : "Nhập tài khoản để mở hộp thư.");
            form.revalidate(); form.repaint();
        });
        submit.addActionListener(e -> {
            final int sp, pp;
            final String h = host.getText().trim(), em = email.getText().trim().toLowerCase(java.util.Locale.ROOT);
            final String pw = new String(pass.getPassword()), fullName = name.getText().trim();
            final boolean creating = register.isSelected();
            try {
                sp = Integer.parseInt(smtp.getText().trim()); pp = Integer.parseInt(pop.getText().trim());
                if (sp < 1 || sp > 65535 || pp < 1 || pp > 65535 || h.isEmpty()) throw new IllegalArgumentException();
                if (!em.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") || pw.isEmpty())
                    throw new IllegalArgumentException("Vui lòng nhập email hợp lệ và mật khẩu.");
                if (creating && (fullName.isEmpty() || fullName.length() > 100 || pw.length() < 6 || pw.length() > 128 || !pw.equals(new String(confirm.getPassword()))))
                    throw new IllegalArgumentException("Nhập họ tên, mật khẩu 6-128 ký tự và xác nhận trùng khớp.");
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(d, ex.getMessage() == null ? "Host hoặc cổng không hợp lệ." : ex.getMessage()); return;
            }
            submit.setEnabled(false); register.setEnabled(false); status.setForeground(MUTED); status.setText("Đang kết nối server...");
            for (JTextField f : new JTextField[]{host, smtp, pop, email, pass, name, confirm}) f.setEnabled(false);
            new SwingWorker<Void, Void>() {
                protected Void doInBackground() throws Exception {
                    AccountClient.execute(h, pp, em, pw, creating ? fullName : null); return null;
                }
                protected void done() {
                    submit.setEnabled(true); register.setEnabled(true);
                    for (JTextField f : new JTextField[]{host, smtp, pop, email, pass}) f.setEnabled(true);
                    name.setEnabled(register.isSelected()); confirm.setEnabled(register.isSelected());
                    try {
                        get();
                        if (!d.isDisplayable()) return;
                        if (creating) {
                            register.setSelected(false); name.setEnabled(false); confirm.setEnabled(false); extra.setVisible(false);
                            welcome.setText("Chào mừng trở lại"); introHint.setText("Đăng nhập để tiếp tục cuộc trò chuyện của bạn."); form.revalidate(); status.setForeground(GREEN);
                            submit.setText("Đăng nhập  →"); pass.setText(""); confirm.setText("");
                            status.setText("Đăng ký thành công. Hãy đăng nhập."); return;
                        }
                        serverHost = h; smtpPort = sp; pop3Port = pp; myEmail = em; myPassword = pw;
                        sessionVersion++; d.dispose(); initUI(); setVisible(true); refreshInbox();
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                        status.setForeground(new Color(191, 61, 75));
                        String message = cause instanceof java.net.SocketTimeoutException ? "Không kết nối được server. Kiểm tra IP, cổng POP3 và bật Mail Server."
                                : cause instanceof java.net.ConnectException ? "Server chưa sẵn sàng. Hãy khởi động Mail Server và kiểm tra IP/cổng."
                                : cause.getMessage() != null && cause.getMessage().contains("Invalid password") ? "Email hoặc mật khẩu không đúng. Vui lòng thử lại."
                                : cause.getMessage();
                        status.setText("<html>" + escape(message == null ? "Không thể kết nối server." : message) + "</html>");
                    }
                }
            }.execute();
        });
        shell.add(form); d.setContentPane(shell);
        d.getRootPane().setDefaultButton(submit);
        d.setSize(940, 750); d.setResizable(false); d.setLocationRelativeTo(null); d.setVisible(true);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        MailTheme.install();
        SwingUtilities.invokeLater(MailClientGUI::new);
    }
}
