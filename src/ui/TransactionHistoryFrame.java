package ui;

import dao.TransactionDAO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import database.DatabaseConnection;

public class TransactionHistoryFrame extends JFrame {

    private JTable transactionTable;
    private DefaultTableModel tableModel;

    public TransactionHistoryFrame() {

        setTitle("Transaction History");
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        JLabel title = new JLabel(
                "TRANSACTION HISTORY",
                SwingConstants.CENTER
        );

        title.setFont(
                new Font("Arial", Font.BOLD, 24)
        );

        add(title, BorderLayout.NORTH);

        String[] columns = {
                "Transaction ID",
                "Medicine",
                "Quantity",
                "Amount",
                "Date"
        };

        tableModel = new DefaultTableModel(columns, 0);

        transactionTable = new JTable(tableModel);

        JScrollPane scrollPane =
                new JScrollPane(transactionTable);

        add(scrollPane, BorderLayout.CENTER);

        loadTransactions();

        setVisible(true);
    }

    private void loadTransactions() {

        String sql =
                "SELECT transaction_id, medicine_name, " +
                "quantity, total_amount, transaction_date " +
                "FROM transactions " +
                "ORDER BY transaction_date DESC";

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result =
                     statement.executeQuery()) {

            while (result.next()) {

                Object[] row = {
                        result.getInt("transaction_id"),
                        result.getString("medicine_name"),
                        result.getInt("quantity"),
                        "₹" + result.getDouble("total_amount"),
                        result.getTimestamp("transaction_date")
                };

                tableModel.addRow(row);
            }

        } catch (Exception e) {

            e.printStackTrace();

            JOptionPane.showMessageDialog(
                    this,
                    "Unable to load transactions.",
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    public static void main(String[] args) {
        new TransactionHistoryFrame();
    }
}