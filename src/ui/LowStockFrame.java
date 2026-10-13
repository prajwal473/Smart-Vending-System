package ui;

import database.DatabaseConnection;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LowStockFrame extends JFrame {

    private JTable stockTable;
    private DefaultTableModel tableModel;

    public LowStockFrame() {

        setTitle("Low Stock Report");
        setSize(700, 450);

        setDefaultCloseOperation(
                JFrame.DISPOSE_ON_CLOSE
        );

        setLocationRelativeTo(null);

        // ---------------- TITLE ----------------

        JLabel title = new JLabel(
                "LOW STOCK REPORT",
                SwingConstants.CENTER
        );

        title.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        24
                )
        );

        add(title, BorderLayout.NORTH);

        // ---------------- TABLE ----------------

        String[] columns = {
                "Medicine ID",
                "Medicine Name",
                "Category",
                "Current Stock",
                "Slot"
        };

        tableModel =
                new DefaultTableModel(
                        columns,
                        0
                );

        stockTable =
                new JTable(tableModel);

        JScrollPane scrollPane =
                new JScrollPane(stockTable);

        add(
                scrollPane,
                BorderLayout.CENTER
        );

        // ---------------- REFRESH BUTTON ----------------

        JButton refreshButton =
                new JButton("Refresh");

        refreshButton.addActionListener(
                e -> loadLowStock()
        );

        add(
                refreshButton,
                BorderLayout.SOUTH
        );

        loadLowStock();

        setVisible(true);
    }

    // =====================================================
    // LOAD LOW STOCK
    // =====================================================

    private void loadLowStock() {

        tableModel.setRowCount(0);

        String sql =
                "SELECT medicine_id, medicine_name, " +
                "category, stock, slot_number " +
                "FROM medicines " +
                "WHERE stock <= 5 " +
                "ORDER BY stock ASC";

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet result =
                        statement.executeQuery()
        ) {

            boolean found = false;

            while (result.next()) {

                found = true;

                Object[] row = {

                        result.getInt(
                                "medicine_id"
                        ),

                        result.getString(
                                "medicine_name"
                        ),

                        result.getString(
                                "category"
                        ),

                        result.getInt(
                                "stock"
                        ),

                        result.getInt(
                                "slot_number"
                        )
                };

                tableModel.addRow(row);
            }

            if (!found) {

                JOptionPane.showMessageDialog(
                        this,
                        "No medicines are currently low in stock.",
                        "Low Stock",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }

        } catch (Exception e) {

            e.printStackTrace();

            JOptionPane.showMessageDialog(
                    this,
                    "Unable to load low stock report.",
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // =====================================================
    // MAIN
    // =====================================================

    public static void main(String[] args) {

        new LowStockFrame();
    }
}
