package ui;

import database.DatabaseConnection;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SalesReportFrame extends JFrame {

    private JLabel transactionsLabel;
    private JLabel itemsLabel;
    private JLabel salesLabel;

    public SalesReportFrame() {

        setTitle("Sales Report");
        setSize(500, 350);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        // ---------------- TITLE ----------------

        JLabel title = new JLabel(
                "SALES REPORT",
                SwingConstants.CENTER
        );

        title.setFont(
                new Font("Arial", Font.BOLD, 26)
        );

        add(title, BorderLayout.NORTH);

        // ---------------- REPORT PANEL ----------------

        JPanel reportPanel = new JPanel(
                new GridLayout(3, 1, 15, 15)
        );

        reportPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        40, 60, 40, 60
                )
        );

        transactionsLabel =
                new JLabel(
                        "Total Transactions: 0",
                        SwingConstants.CENTER
                );

        itemsLabel =
                new JLabel(
                        "Total Items Sold: 0",
                        SwingConstants.CENTER
                );

        salesLabel =
                new JLabel(
                        "Total Sales: ₹0.00",
                        SwingConstants.CENTER
                );

        transactionsLabel.setFont(
                new Font("Arial", Font.BOLD, 18)
        );

        itemsLabel.setFont(
                new Font("Arial", Font.BOLD, 18)
        );

        salesLabel.setFont(
                new Font("Arial", Font.BOLD, 18)
        );

        reportPanel.add(transactionsLabel);
        reportPanel.add(itemsLabel);
        reportPanel.add(salesLabel);

        add(reportPanel, BorderLayout.CENTER);

        // ---------------- BUTTON ----------------

        JButton refreshButton =
                new JButton("Refresh Report");

        refreshButton.addActionListener(e ->
                loadReport()
        );

        add(refreshButton, BorderLayout.SOUTH);

        // Load report when window opens
        loadReport();

        setVisible(true);
    }

    // =====================================================
    // LOAD SALES REPORT
    // =====================================================

    private void loadReport() {

        String sql =
                "SELECT " +
                "COUNT(*) AS total_transactions, " +
                "COALESCE(SUM(quantity), 0) AS total_items, " +
                "COALESCE(SUM(total_amount), 0) AS total_sales " +
                "FROM transactions";

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet result =
                        statement.executeQuery()
        ) {

            if (result.next()) {

                int totalTransactions =
                        result.getInt(
                                "total_transactions"
                        );

                int totalItems =
                        result.getInt(
                                "total_items"
                        );

                double totalSales =
                        result.getDouble(
                                "total_sales"
                        );

                transactionsLabel.setText(
                        "Total Transactions: "
                                + totalTransactions
                );

                itemsLabel.setText(
                        "Total Items Sold: "
                                + totalItems
                );

                salesLabel.setText(
                        "Total Sales: ₹"
                                + String.format(
                                        "%.2f",
                                        totalSales
                                )
                );
            }

        } catch (Exception e) {

            e.printStackTrace();

            JOptionPane.showMessageDialog(
                    this,
                    "Unable to load sales report.",
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // =====================================================
    // MAIN
    // =====================================================

    public static void main(String[] args) {

        new SalesReportFrame();
    }
}
