import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;

public class EmployeeLeaveApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}

enum LeaveStatus { PENDING, APPROVED, REJECTED }
enum LeaveType   { ANNUAL, SICK, UNPAID }

class Employee {
    int id;
    String name;
    int balance; 

    Employee(int id, String name, int balance) {
        this.id = id; this.name = name; this.balance = balance;
    }
}

class LeaveApplication {
    int id; 
    int employeeId;
    LeaveType type;
    LocalDate start;
    LocalDate end;
    int days; 
    LeaveStatus status;
    String reason;

    LeaveApplication(int id, int employeeId, LeaveType type, LocalDate start, LocalDate end, int days, LeaveStatus status, String reason) {
        this.id = id; this.employeeId = employeeId; this.type = type; this.start = start; this.end = end; this.days = days; this.status = status; this.reason = reason;
    }
}


class DataStore {
    private static final Path DATA_DIR = Paths.get("data");
    private static final Path EMP_FILE = DATA_DIR.resolve("employees.csv");
    private static final Path LEAVE_FILE = DATA_DIR.resolve("leaves.csv");
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    static Map<Integer, Employee> loadEmployees() {
        ensureDataDir();
        Map<Integer, Employee> map = new LinkedHashMap<>();
        if (!Files.exists(EMP_FILE)) {
            // seed data
            map.put(1001, new Employee(1001, "Alice", 20));
            map.put(1002, new Employee(1002, "Bob", 20));
            saveEmployees(map);
            return map;
        }
        try (BufferedReader br = Files.newBufferedReader(EMP_FILE, StandardCharsets.UTF_8)) {
            String line; boolean headerSkipped = false;
            while ((line = br.readLine()) != null) {
                if (!headerSkipped) { headerSkipped = true; continue; }
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                int id = Integer.parseInt(parts[0]);
                String name = unescape(parts[1]);
                int balance = Integer.parseInt(parts[2]);
                map.put(id, new Employee(id, name, balance));
            }
        } catch (IOException e) { e.printStackTrace(); }
        return map;
    }

    static void saveEmployees(Map<Integer, Employee> map) {
        ensureDataDir();
        try (BufferedWriter bw = Files.newBufferedWriter(EMP_FILE, StandardCharsets.UTF_8)) {
            bw.write("id,name,balance\n");
            for (Employee e : map.values()) {
                bw.write(e.id + "," + escape(e.name) + "," + e.balance + "\n");
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    static List<LeaveApplication> loadLeaves() {
        ensureDataDir();
        List<LeaveApplication> list = new ArrayList<>();
        if (!Files.exists(LEAVE_FILE)) {
            saveLeaves(list);
            return list;
        }
        try (BufferedReader br = Files.newBufferedReader(LEAVE_FILE, StandardCharsets.UTF_8)) {
            String line; boolean headerSkipped = false;
            while ((line = br.readLine()) != null) {
                if (!headerSkipped) { headerSkipped = true; continue; }
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",", -1);
                int id = Integer.parseInt(p[0]);
                int empId = Integer.parseInt(p[1]);
                LeaveType type = LeaveType.valueOf(p[2]);
                LocalDate start = LocalDate.parse(p[3], DF);
                LocalDate end = LocalDate.parse(p[4], DF);
                int days = Integer.parseInt(p[5]);
                LeaveStatus status = LeaveStatus.valueOf(p[6]);
                String reason = unescape(p[7]);
                list.add(new LeaveApplication(id, empId, type, start, end, days, status, reason));
            }
        } catch (IOException e) { e.printStackTrace(); }
        return list;
    }

    static void saveLeaves(List<LeaveApplication> list) {
        ensureDataDir();
        try (BufferedWriter bw = Files.newBufferedWriter(LEAVE_FILE, StandardCharsets.UTF_8)) {
            bw.write("id,employeeId,type,start,end,days,status,reason\n");
            for (LeaveApplication l : list) {
                bw.write(l.id + "," + l.employeeId + "," + l.type.name() + "," + l.start + "," + l.end + "," + l.days + "," + l.status.name() + "," + escape(l.reason) + "\n");
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    static int nextLeaveId(List<LeaveApplication> list) {
        return list.stream().mapToInt(l -> l.id).max().orElse(1000) + 1;
    }

    private static void ensureDataDir() {
        try { Files.createDirectories(DATA_DIR); } catch (IOException ignored) {}
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace(",", "\\,");
    }
    private static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        boolean esc = false;
        for (char c : s.toCharArray()) {
            if (esc) { out.append(c); esc = false; }
            else if (c == '\\') { esc = true; }
            else out.append(c);
        }
        return out.toString();
    }
}

class LeaveService {
    private final Map<Integer, Employee> employees;
    private final List<LeaveApplication> leaves;

    LeaveService() {
        this.employees = DataStore.loadEmployees();
        this.leaves = DataStore.loadLeaves();
    }

    Collection<Employee> getAllEmployees() { return employees.values(); }
    Employee getEmployee(int id) { return employees.get(id); }
    List<LeaveApplication> getLeavesForEmployee(int empId) {
        List<LeaveApplication> res = new ArrayList<>();
        for (LeaveApplication l : leaves) if (l.employeeId == empId) res.add(l);
        return res;
    }
    List<LeaveApplication> getAllLeaves() { return leaves; }
    List<LeaveApplication> getPendingLeaves() {
        List<LeaveApplication> res = new ArrayList<>();
        for (LeaveApplication l : leaves) if (l.status == LeaveStatus.PENDING) res.add(l);
        return res;
    }

    LeaveApplication applyLeave(int empId, LeaveType type, LocalDate start, LocalDate end, String reason) throws Exception {
        if (end.isBefore(start)) throw new Exception("End date cannot be before start date");
        int days = (int) ChronoUnit.DAYS.between(start, end) + 1;
        Employee emp = employees.get(empId);
        if (emp == null) throw new Exception("Employee not found");
        if (type == LeaveType.ANNUAL && emp.balance < days) throw new Exception("Insufficient leave balance");
        int id = DataStore.nextLeaveId(leaves);
        LeaveApplication app = new LeaveApplication(id, empId, type, start, end, days, LeaveStatus.PENDING, reason == null ? "" : reason);
        leaves.add(app);
        DataStore.saveLeaves(leaves);
        return app;
    }

    void approveLeave(int leaveId) throws Exception {
        LeaveApplication l = findById(leaveId);
        if (l.status != LeaveStatus.PENDING) throw new Exception("Only pending requests can be approved");
        l.status = LeaveStatus.APPROVED;
        if (l.type == LeaveType.ANNUAL) {
            Employee e = employees.get(l.employeeId);
            e.balance -= l.days;
            if (e.balance < 0) e.balance = 0; // safety
            DataStore.saveEmployees(employees);
        }
        DataStore.saveLeaves(leaves);
    }

    void rejectLeave(int leaveId) throws Exception {
        LeaveApplication l = findById(leaveId);
        if (l.status != LeaveStatus.PENDING) throw new Exception("Only pending requests can be rejected");
        l.status = LeaveStatus.REJECTED;
        DataStore.saveLeaves(leaves);
    }

    private LeaveApplication findById(int id) throws Exception {
        for (LeaveApplication l : leaves) if (l.id == id) return l;
        throw new Exception("Leave not found: " + id);
    }
}

// ======== UI ========
class LoginFrame extends JFrame {
    private final JTextField txtUser = new JTextField();
    private final JRadioButton rbEmployee = new JRadioButton("Employee", true);
    private final JRadioButton rbAdmin = new JRadioButton("Admin");
    private final LeaveService service = new LeaveService();

    LoginFrame() {
        super("Leave Management — Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(420, 220);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(16,16,16,16));
        setContentPane(root);

        JLabel title = new JLabel("Employee Leave Management", JLabel.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(3,1,8,8));
        JPanel row1 = new JPanel(new BorderLayout(8,8));
        row1.add(new JLabel("Username / Emp ID:"), BorderLayout.WEST);
        row1.add(txtUser, BorderLayout.CENTER);
        form.add(row1);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ButtonGroup g = new ButtonGroup(); g.add(rbEmployee); g.add(rbAdmin);
        row2.add(new JLabel("Role:"));
        row2.add(rbEmployee); row2.add(rbAdmin);
        form.add(row2);

        JButton btnLogin = new JButton(new AbstractAction("Login") {
            @Override public void actionPerformed(ActionEvent e) { doLogin(); }
        });
        form.add(btnLogin);

        root.add(form, BorderLayout.CENTER);

        // Hints
        JTextArea hint = new JTextArea("Hints:\n - Admin: username 'admin'\n - Employee IDs: 1001 (Alice), 1002 (Bob)");
        hint.setEditable(false); hint.setOpaque(false);
        root.add(hint, BorderLayout.SOUTH);
    }

    private void doLogin() {
        try {
            if (rbAdmin.isSelected()) {
                String u = txtUser.getText().trim();
                if ("admin".equalsIgnoreCase(u)) {
                    new AdminFrame(service).setVisible(true);
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid admin username", "Error", JOptionPane.ERROR_MESSAGE);
                }
                return;
            }
            // employee
            int empId = Integer.parseInt(txtUser.getText().trim());
            Employee emp = service.getEmployee(empId);
            if (emp == null) throw new NumberFormatException();
            new EmployeeFrame(service, emp).setVisible(true);
            dispose();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid Employee ID (e.g., 1001)", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

class EmployeeFrame extends JFrame {
    private final LeaveService service;
    private final Employee employee;

    private final JLabel lblName = new JLabel();
    private final JLabel lblBalance = new JLabel();

    private final JComboBox<LeaveType> cbType = new JComboBox<>(LeaveType.values());
    private final JTextField tfStart = new JTextField("2025-08-22");
    private final JTextField tfEnd   = new JTextField("2025-08-22");
    private final JTextField tfReason= new JTextField();

    private final DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Type","Start","End","Days","Status","Reason"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };

    EmployeeFrame(LeaveService service, Employee employee) {
        super("Employee Portal — " + employee.name + " (" + employee.id + ")");
        this.service = service; this.employee = employee;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 520);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12,12));
        root.setBorder(new EmptyBorder(12,12,12,12));
        setContentPane(root);

        JPanel header = new JPanel(new GridLayout(2,1));
        lblName.setText("Welcome, " + employee.name + " (#" + employee.id + ")");
        lblName.setFont(lblName.getFont().deriveFont(Font.BOLD, 16f));
        header.add(lblName);
        updateBalanceLabel();
        header.add(lblBalance);
        root.add(header, BorderLayout.NORTH);

        // Apply panel
        JPanel applyPanel = new JPanel(new GridLayout(5,2,8,8));
        applyPanel.setBorder(BorderFactory.createTitledBorder("Apply for Leave"));
        applyPanel.add(new JLabel("Type:")); applyPanel.add(cbType);
        applyPanel.add(new JLabel("Start (yyyy-MM-dd):")); applyPanel.add(tfStart);
        applyPanel.add(new JLabel("End (yyyy-MM-dd):"));   applyPanel.add(tfEnd);
        applyPanel.add(new JLabel("Reason:")); applyPanel.add(tfReason);
        JButton btnApply = new JButton(new AbstractAction("Submit Application") {
            @Override public void actionPerformed(ActionEvent e) { submitApplication(); }
        });
        applyPanel.add(new JLabel()); applyPanel.add(btnApply);

        // History table
        JTable table = new JTable(model);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder("Your Leave History"));

        root.add(applyPanel, BorderLayout.WEST);
        root.add(scroll, BorderLayout.CENTER);

        refreshHistory();
    }

    private void updateBalanceLabel() {
        lblBalance.setText("Annual Leave Balance: " + employee.balance + " day(s)");
    }

    private void submitApplication() {
        try {
            LeaveType type = (LeaveType) cbType.getSelectedItem();
            LocalDate start = LocalDate.parse(tfStart.getText().trim());
            LocalDate end   = LocalDate.parse(tfEnd.getText().trim());
            String reason   = tfReason.getText().trim();
            service.applyLeave(employee.id, type, start, end, reason);
            JOptionPane.showMessageDialog(this, "Leave application submitted!", "Success", JOptionPane.INFORMATION_MESSAGE);
            refreshHistory();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshHistory() {
        // Reload employee in case balance changed from admin actions
        Employee updated = service.getEmployee(employee.id);
        employee.balance = updated.balance;
        updateBalanceLabel();

        model.setRowCount(0);
        for (LeaveApplication l : service.getLeavesForEmployee(employee.id)) {
            model.addRow(new Object[]{ l.id, l.type, l.start, l.end, l.days, l.status, l.reason });
        }
    }
}

class AdminFrame extends JFrame {
    private final LeaveService service;
    private final DefaultTableModel modelAll = new DefaultTableModel(new String[]{"ID","EmpID","Name","Type","Start","End","Days","Status","Reason"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(modelAll);

    AdminFrame(LeaveService service) {
        super("Admin Portal");
        this.service = service;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 560);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12,12));
        root.setBorder(new EmptyBorder(12,12,12,12));
        setContentPane(root);

        JLabel title = new JLabel("Pending & All Leave Requests", JLabel.LEFT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        root.add(title, BorderLayout.NORTH);

        root.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnApprove = new JButton(new AbstractAction("Approve Selected") {
            @Override public void actionPerformed(ActionEvent e) { bulkApprove(); }
        });
        JButton btnReject = new JButton(new AbstractAction("Reject Selected") {
            @Override public void actionPerformed(ActionEvent e) { bulkReject(); }
        });
        JButton btnRefresh = new JButton(new AbstractAction("Refresh") {
            @Override public void actionPerformed(ActionEvent e) { refresh(); }
        });
        actions.add(btnRefresh); actions.add(btnApprove); actions.add(btnReject);
        root.add(actions, BorderLayout.SOUTH);

        refresh();
    }

    private void refresh() {
        modelAll.setRowCount(0);
        Map<Integer, Employee> empMap = new LinkedHashMap<>();
        for (Employee e : service.getAllEmployees()) empMap.put(e.id, e);
        for (LeaveApplication l : service.getAllLeaves()) {
            Employee e = empMap.get(l.employeeId);
            modelAll.addRow(new Object[]{ l.id, l.employeeId, e == null ? "?" : e.name, l.type, l.start, l.end, l.days, l.status, l.reason });
        }
    }

    private Integer selectedLeaveId() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        Object v = table.getValueAt(row, 0);
        return (v instanceof Integer) ? (Integer) v : Integer.parseInt(v.toString());
    }

    private void bulkApprove() {
        Integer id = selectedLeaveId();
        if (id == null) { JOptionPane.showMessageDialog(this, "Select a row first"); return; }
        try {
            service.approveLeave(id);
            JOptionPane.showMessageDialog(this, "Approved");
            refresh();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void bulkReject() {
        Integer id = selectedLeaveId();
        if (id == null) { JOptionPane.showMessageDialog(this, "Select a row first"); return; }
        try {
            service.rejectLeave(id);
            JOptionPane.showMessageDialog(this, "Rejected");
            refresh();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
