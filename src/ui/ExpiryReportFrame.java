package ui;

import database.DatabaseConnection;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

public class ExpiryReportFrame extends JFrame {

    private JTable expiryTable;
    private DefaultTableModel tableModel;

    public ExpiryReportFrame() {

        setTitle("Expiry Management");
        setSize(750, 450);

        setDefaultCloseOperation(
                JFrame.DISPOSE_ON_CLOSE
        );

        setLocationRelativeTo(null);

        // ---------------- TITLE ----------------

        JLabel title = new JLabel(
                "MEDICINE EXPIRY MANAGEMENT",
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
                "Expiry Date",
                "Stock",
                "Status"
        };

        tableModel =
                new DefaultTableModel(
                        columns,
                        0
                );

        expiryTable =
                new JTable(tableModel);

        JScrollPane scrollPane =
                new JScrollPane(expiryTable);

        add(
                scrollPane,
                BorderLayout.CENTER
        );

        // ---------------- REFRESH ----------------

        JButton refreshButton =
                new JButton("Refresh");

        refreshButton.addActionListener(
                e -> loadExpiryData()
        );

        add(
                refreshButton,
                BorderLayout.SOUTH
        );

        loadExpiryData();

        setVisible(true);
    }

    // =====================================================
    // LOAD EXPIRY DATA
    // =====================================================

    private void loadExpiryData() {

        tableModel.setRowCount(0);

        String sql =
                "SELECT medicine_id, medicine_name, " +
                "expiry_date, stock " +
                "FROM medicines " +
                "ORDER BY expiry_date ASC";

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet result =
                        statement.executeQuery()
        ) {

            LocalDate today =
                    LocalDate.now();

            while (result.next()) {

                int medicineId =
                        result.getInt(
                                "medicine_id"
                        );

                String medicineName =
                        result.getString(
                                "medicine_name"
                        );

                LocalDate expiryDate =
                        result.getDate(
                                "expiry_date"
                        ).toLocalDate();

                int stock =
                        result.getInt(
                                "stock"
                        );

                String status;

                if (expiryDate.isBefore(today)) {

                    status = "EXPIRED";

                } else if (
                        !expiryDate.isAfter(
                                today.plusDays(30)
                        )
                ) {

                    status = "EXPIRING SOON";

                } else {

                    status = "VALID";
                }

                Object[] row = {

                        medicineId,

                        medicineName,

                        expiryDate,

                        stock,

                        status
                };

                tableModel.addRow(row);
            }

        } catch (Exception e) {

            e.printStackTrace();

            JOptionPane.showMessageDialog(
                    this,
                    "Unable to load expiry report.",
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // =====================================================
    // MAIN
    // =====================================================

    public static void main(String[] args) {

        new ExpiryReportFrame();
    }
}