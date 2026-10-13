package ui;

import dao.MedicineDAO;

import javax.swing.*;
import java.awt.*;

public class AdminMedicineFrame extends JFrame {

    private JTextField nameField;
    private JTextField categoryField;
    private JTextField priceField;
    private JTextField stockField;
    private JTextField slotField;
    private JTextField expiryField;

    private JCheckBox prescriptionCheckBox;

    private JButton addButton;

    private MedicineDAO medicineDAO;

    public AdminMedicineFrame() {

        medicineDAO = new MedicineDAO();

        setTitle("Admin - Add Medicine");
        setSize(500, 500);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        // ---------------- TITLE ----------------

        JLabel title = new JLabel(
                "ADD MEDICINE",
                SwingConstants.CENTER
        );

        title.setFont(
                new Font("Arial", Font.BOLD, 24)
        );

        add(title, BorderLayout.NORTH);

        // ---------------- FORM ----------------

        JPanel panel = new JPanel(
                new GridLayout(8, 2, 10, 10)
        );

        panel.setBorder(
                BorderFactory.createEmptyBorder(
                        20, 30, 20, 30
                )
        );

        panel.add(new JLabel("Medicine Name:"));

        nameField = new JTextField();
        panel.add(nameField);

        panel.add(new JLabel("Category:"));

        categoryField = new JTextField();
        panel.add(categoryField);

        panel.add(new JLabel("Price:"));

        priceField = new JTextField();
        panel.add(priceField);

        panel.add(new JLabel("Stock:"));

        stockField = new JTextField();
        panel.add(stockField);

        panel.add(new JLabel("Slot Number:"));

        slotField = new JTextField();
        panel.add(slotField);

        panel.add(new JLabel("Expiry Date:"));

        expiryField = new JTextField();
        panel.add(expiryField);

        panel.add(new JLabel("Prescription Required:"));

        prescriptionCheckBox =
                new JCheckBox("Yes");

        panel.add(prescriptionCheckBox);

        panel.add(new JLabel(""));

        addButton =
                new JButton("Add Medicine");

        panel.add(addButton);

        addButton.addActionListener(
                e -> addMedicine()
        );

        add(panel, BorderLayout.CENTER);

        setVisible(true);
    }

    // =====================================================
    // ADD MEDICINE
    // =====================================================

    private void addMedicine() {

        try {

            String name =
                    nameField.getText().trim();

            String category =
                    categoryField.getText().trim();

            double price =
                    Double.parseDouble(
                            priceField.getText()
                    );

            int stock =
                    Integer.parseInt(
                            stockField.getText()
                    );

            int slot =
                    Integer.parseInt(
                            slotField.getText()
                    );

            String expiry =
                    expiryField.getText().trim();

            boolean prescriptionRequired =
                    prescriptionCheckBox.isSelected();

            // ---------------- VALIDATION ----------------

            if (name.isEmpty() ||
                    category.isEmpty() ||
                    expiry.isEmpty()) {

                JOptionPane.showMessageDialog(
                        this,
                        "Please fill all fields.",
                        "Missing Information",
                        JOptionPane.WARNING_MESSAGE
                );

                return;
            }

            if (price <= 0 ||
                    stock < 0 ||
                    slot <= 0) {

                JOptionPane.showMessageDialog(
                        this,
                        "Please enter valid values.",
                        "Invalid Input",
                        JOptionPane.WARNING_MESSAGE
                );

                return;
            }

            // ---------------- DATABASE ----------------

            boolean success =
                    medicineDAO.addMedicine(
                            name,
                            category,
                            price,
                            stock,
                            slot,
                            expiry,
                            prescriptionRequired
                    );

            if (success) {

                JOptionPane.showMessageDialog(
                        this,
                        "Medicine added successfully!"
                );

                // Clear fields

                nameField.setText("");
                categoryField.setText("");
                priceField.setText("");
                stockField.setText("");
                slotField.setText("");
                expiryField.setText("");

                prescriptionCheckBox.setSelected(false);

            } else {

                JOptionPane.showMessageDialog(
                        this,
                        "Unable to add medicine.",
                        "Database Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }

        } catch (NumberFormatException ex) {

            JOptionPane.showMessageDialog(
                    this,
                    "Price, stock and slot must contain valid numbers.",
                    "Invalid Input",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // =====================================================
    // MAIN
    // =====================================================

    public static void main(String[] args) {

        new AdminMedicineFrame();
    }
}