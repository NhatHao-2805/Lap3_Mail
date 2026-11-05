package ui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/** Small dependency-free visual system shared by the Swing client and server. */
public final class MailTheme {
    public static final Color INK = new Color(26, 38, 61), MUTED = new Color(112, 126, 149),
            BLUE = new Color(49, 99, 233), NAVY = new Color(19, 32, 57),
            BG = new Color(242, 245, 250), LINE = new Color(225, 232, 242),
            PALE = new Color(234, 240, 255), GREEN = new Color(27, 137, 104);
    private MailTheme() { }
    public static void install() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) { }
        Font font = new Font("Segoe UI", Font.PLAIN, 13);
        for (Object key : UIManager.getDefaults().keySet().toArray())
            if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource) UIManager.put(key, font);
        UIManager.put("Panel.background", Color.WHITE);
        UIManager.put("Label.foreground", INK);
        UIManager.put("TextField.selectionBackground", PALE);
        UIManager.put("PasswordField.selectionBackground", PALE);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("TabbedPane.selected", Color.WHITE);
        UIManager.put("TabbedPane.contentAreaColor", Color.WHITE);
        UIManager.put("TableHeader.background", BG);
        UIManager.put("TableHeader.foreground", MUTED);
    }
    public static JLabel label(String text, int size, Color color, boolean bold) {
        JLabel l = new JLabel(text); l.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(color); return l;
    }
    public static JPanel panel(LayoutManager layout, Color color, int padding) {
        JPanel p = new JPanel(layout); p.setBackground(color);
        p.setBorder(new EmptyBorder(padding, padding, padding, padding)); return p;
    }
    public static JButton button(String text, boolean primary) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setForeground(primary ? Color.WHITE : INK);
        b.setBackground(primary ? BLUE : Color.WHITE);
        b.setFocusPainted(false); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new CompoundBorder(new LineBorder(primary ? BLUE : LINE, 1, true), new EmptyBorder(11, 18, 11, 18)));
        return b;
    }
    public static void input(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14)); field.setForeground(INK);
        field.setBorder(new CompoundBorder(new LineBorder(LINE, 1, true), new EmptyBorder(9, 12, 9, 12)));
        field.setBackground(Color.WHITE);
    }
    public static JPanel field(String title, JComponent component) {
        JPanel p = panel(new BorderLayout(0, 6), Color.WHITE, 0);
        p.add(label(title, 12, MUTED, true), BorderLayout.NORTH); p.add(component);
        if (component instanceof JTextField) input((JTextField) component);
        return p;
    }
    public static JScrollPane scroll(Component c) {
        JScrollPane s = new JScrollPane(c); s.setBorder(new EmptyBorder(0, 0, 0, 0));
        s.getViewport().setBackground(Color.WHITE); s.getVerticalScrollBar().setUnitIncrement(16); return s;
    }
    public static void table(JTable t) {
        t.setRowHeight(54); t.setShowGrid(false); t.setIntercellSpacing(new Dimension(0, 0));
        t.setSelectionBackground(PALE); t.setSelectionForeground(INK); t.setForeground(INK);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 13)); t.setFillsViewportHeight(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.getTableHeader().setPreferredSize(new Dimension(0, 40));
        t.getTableHeader().setReorderingAllowed(false); t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(table, value, selected, false, row, col);
                setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, LINE), new EmptyBorder(0, 14, 0, 12)));
                setBackground(selected ? PALE : Color.WHITE);
                setFont(new Font("Segoe UI", col == 2 ? Font.BOLD : Font.PLAIN, 13));
                setToolTipText(value == null ? "" : value.toString()); return this;
            }
        });
    }
    public static JPanel brand() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, NAVY, getWidth(), getHeight(), new Color(35, 65, 120)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 9));
                g2.fillOval(-120, 170, 400, 400); g2.fillOval(100, 330, 350, 350);
                g2.setColor(new Color(255, 255, 255, 35)); g2.setStroke(new BasicStroke(2));
                int x = 38, y = 185;
                g2.drawRoundRect(x, y, 210, 136, 22, 22);
                g2.drawLine(x + 5, y + 8, x + 105, y + 78); g2.drawLine(x + 205, y + 8, x + 105, y + 78);
                g2.dispose();
            }
        };
        p.setLayout(new BorderLayout()); p.setBorder(new EmptyBorder(36, 32, 34, 26));
        p.setPreferredSize(new Dimension(300, 0));
        JPanel top = new JPanel(new GridLayout(2, 1, 0, 10)); top.setOpaque(false);
        top.add(label("VKU / MAIL", 25, Color.WHITE, true));
        top.add(label("KẾT NỐI MỌI CUỘC TRÒ CHUYỆN", 10, new Color(158, 184, 227), true));
        p.add(top, BorderLayout.NORTH);
        JPanel bottom = new JPanel(new GridLayout(0, 1, 0, 12)); bottom.setOpaque(false);
        bottom.add(label("Một không gian.", 25, Color.WHITE, true));
        bottom.add(label("Mọi kết nối.", 25, Color.WHITE, true));
        bottom.add(label("Gửi thư, chia sẻ và giữ liên lạc.", 13, new Color(187, 204, 231), false));
        bottom.add(label("LẬP TRÌNH MẠNG  /  LAB 03", 11, new Color(158, 184, 227), true));
        p.add(bottom, BorderLayout.SOUTH); return p;
    }
}
