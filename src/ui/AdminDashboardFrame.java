package ui;

import java.awt.*;
import javax.swing.*;

public class AdminDashboardFrame extends JFrame {

    public AdminDashboardFrame() {

        setTitle("Admin Dashboard");
        setSize(600, 450);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        // ---------------- TITLE ----------------

        JLabel title = new JLabel(
                "ADMIN DASHBOARD",
                SwingConstants.CENTER
        );

        title.setFont(
                new Font("Arial", Font.BOLD, 26)
        );

        add(title, BorderLayout.NORTH);

        // ---------------- BUTTON PANEL ----------------

        JPanel buttonPanel = new JPanel(
                new GridLayout(7, 1, 15, 15)
        );

        buttonPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        30, 100, 30, 100
                )
        );

        // Add Medicine

        JButton addMedicineButton =
                new JButton("Add Medicine");

        addMedicineButton.addActionListener(e -> {
            new AdminMedicineFrame();
        });

        buttonPanel.add(addMedicineButton);

        // Stock Management

        JButton stockButton =
                new JButton("Manage Stock");

        stockButton.addActionListener(e -> {
            new AdminStockFrame();
        });

        buttonPanel.add(stockButton);
        JButton lowStockButton =
        new JButton("Low Stock Report");

lowStockButton.addActionListener(e -> {
    new LowStockFrame();
});

buttonPanel.add(lowStockButton);
JButton expiryButton =
        new JButton("Expiry Management");

expiryButton.addActionListener(e -> {
    new ExpiryReportFrame();
});

buttonPanel.add(expiryButton);

        // Transaction History

        JButton historyButton =
                new JButton("Transaction History");

        historyButton.addActionListener(e -> {
            new TransactionHistoryFrame();
        });

        buttonPanel.add(historyButton);

        // Sales Report

        JButton salesButton =
                new JButton("Sales Report");

        salesButton.addActionListener(e -> {
    new SalesReportFrame();
});

        buttonPanel.add(salesButton);

        // Logout

        JButton logoutButton =
                new JButton("Logout");

        logoutButton.addActionListener(e -> {

            dispose();

            new AdminLoginFrame();
        });

        buttonPanel.add(logoutButton);

        add(buttonPanel, BorderLayout.CENTER);

        setVisible(true);
    }

    // =====================================================
    // SALES REPORT
    // =====================================================

    private void showSalesReport() {

        JOptionPane.showMessageDialog(
                this,
                "Sales Report module will be implemented next.",
                "Sales Report",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    // =====================================================
    // MAIN
    // =====================================================

    public static void main(String[] args) {

        new AdminDashboardFrame();
    }
}
