package ui;

import dao.MedicineDAO;

import javax.swing.*;
import java.awt.*;

public class AdminStockFrame extends JFrame {

    private JTextField medicineIdField;
    private JTextField quantityField;
    private JButton addStockButton;

    private MedicineDAO medicineDAO;

    public AdminStockFrame() {

        medicineDAO = new MedicineDAO();

        setTitle("Admin - Stock Management");
        setSize(450, 300);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        JLabel title = new JLabel(
                "ADMIN STOCK MANAGEMENT",
                SwingConstants.CENTER
        );

        title.setFont(
                new Font("Arial", Font.BOLD, 20)
        );

        add(title, BorderLayout.NORTH);

        JPanel panel = new JPanel(
                new GridLayout(3, 2, 10, 10)
        );

        panel.setBorder(
                BorderFactory.createEmptyBorder(
                        30, 30, 30, 30
                )
        );

        panel.add(new JLabel("Medicine ID:"));

        medicineIdField = new JTextField();
        panel.add(medicineIdField);

        panel.add(new JLabel("Quantity to Add:"));

        quantityField = new JTextField();
        panel.add(quantityField);

        addStockButton = new JButton("Add Stock");
        panel.add(addStockButton);

        addStockButton.addActionListener(e ->
                addStock()
        );

        add(panel, BorderLayout.CENTER);

        setVisible(true);
    }

    private void addStock() {

        try {

            int medicineId =
                    Integer.parseInt(
                            medicineIdField.getText()
                    );

            int quantity =
                    Integer.parseInt(
                            quantityField.getText()
                    );

            if (quantity <= 0) {

                JOptionPane.showMessageDialog(
                        this,
                        "Quantity must be greater than 0.",
                        "Invalid Quantity",
                        JOptionPane.WARNING_MESSAGE
                );

                return;
            }

            boolean success =
                    medicineDAO.addStock(
                            medicineId,
                            quantity
                    );

            if (success) {

                JOptionPane.showMessageDialog(
                        this,
                        "Stock updated successfully!"
                );

                medicineIdField.setText("");
                quantityField.setText("");

            } else {

                JOptionPane.showMessageDialog(
                        this,
                        "Medicine ID not found.",
                        "Update Failed",
                        JOptionPane.ERROR_MESSAGE
                );
            }

        } catch (NumberFormatException ex) {

            JOptionPane.showMessageDialog(
                    this,
                    "Please enter valid numbers.",
                    "Invalid Input",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    public static void main(String[] args) {
        new AdminStockFrame();
    }
}
