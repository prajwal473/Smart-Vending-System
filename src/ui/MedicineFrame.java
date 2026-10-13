package ui;
import dao.TransactionDAO;
import hardware.HardwareController;
import hardware.SimulatorHardware;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import model.Medicine;
import service.MedicineService;

public class MedicineFrame extends JFrame {

    private MedicineService medicineService;
    private JPanel medicinePanel;

    private HardwareController hardwareController;

    private JButton historyButton;
    private JTextField searchField;

    public MedicineFrame() {

        medicineService = new MedicineService();

        // Hardware simulator
        hardwareController = new SimulatorHardware();

        setTitle("Smart Vending System");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // =====================================================
        // TITLE
        // =====================================================

        JLabel title = new JLabel(
                "SMART VENDING SYSTEM",
                SwingConstants.CENTER
        );

        title.setFont(
                new Font("Arial", Font.BOLD, 24)
        );

        // =====================================================
        // SEARCH
        // =====================================================

        searchField = new JTextField();

        searchField.setPreferredSize(
                new Dimension(250, 30)
        );

        searchField.addActionListener(
                e -> searchMedicines()
        );

        JPanel searchPanel = new JPanel();

        searchPanel.add(
                new JLabel("Search Medicine:")
        );

        searchPanel.add(searchField);

        JPanel topPanel = new JPanel(
                new BorderLayout()
        );

        topPanel.add(
                title,
                BorderLayout.NORTH
        );

        topPanel.add(
                searchPanel,
                BorderLayout.CENTER
        );

        add(
                topPanel,
                BorderLayout.NORTH
        );

        // =====================================================
        // BUTTONS
        // =====================================================

        historyButton =
                new JButton("Transaction History");

        historyButton.addActionListener(e -> {

            new TransactionHistoryFrame();

        });

        JButton adminButton =
                new JButton("Admin Stock");

        adminButton.addActionListener(e -> {

            new AdminStockFrame();

        });

        JButton addMedicineButton =
                new JButton("Add Medicine");

        addMedicineButton.addActionListener(e -> {

            new AdminMedicineFrame();

        });

        JButton adminLoginButton =
                new JButton("Admin Login");

        adminLoginButton.addActionListener(e -> {

            new AdminLoginFrame();

        });

        JPanel bottomPanel = new JPanel();

       
        bottomPanel.add(adminLoginButton);

        add(
                bottomPanel,
                BorderLayout.SOUTH
        );

        // =====================================================
        // MEDICINE PANEL
        // =====================================================

        medicinePanel = new JPanel();

        medicinePanel.setLayout(
                new GridLayout(
                        0,
                        2,
                        15,
                        15
                )
        );

        medicinePanel.setBorder(
                BorderFactory.createEmptyBorder(
                        10,
                        10,
                        10,
                        10
                )
        );

        add(
                new JScrollPane(medicinePanel),
                BorderLayout.CENTER
        );

        // Load medicines
        loadMedicines();

        setVisible(true);
    }

    // =====================================================
    // LOAD MEDICINES
    // =====================================================

    private void loadMedicines() {

        medicinePanel.removeAll();

        List<Medicine> medicines =
                medicineService.getAvailableMedicines();

        System.out.println(
                "Medicines received by GUI: "
                        + medicines.size()
        );

        for (Medicine medicine : medicines) {

            System.out.println(
                    medicine.getMedicineName()
                            + " | Stock: "
                            + medicine.getStock()
                            + " | Expiry: "
                            + medicine.getExpiryDate()
            );

            addMedicineCard(medicine);
        }

        medicinePanel.revalidate();
        medicinePanel.repaint();
    }

    // =====================================================
    // MEDICINE CARD
    // =====================================================

    private void addMedicineCard(
            Medicine medicine) {

        JPanel card = new JPanel();

        card.setBorder(
                BorderFactory.createEtchedBorder()
        );

        card.setLayout(
                new GridLayout(5, 1, 5, 5)
        );

        JLabel name = new JLabel(
                medicine.getMedicineName(),
                SwingConstants.CENTER
        );

        name.setFont(
                new Font("Arial", Font.BOLD, 18)
        );

        JLabel price = new JLabel(
                "Price: ₹"
                        + String.format(
                                "%.2f",
                                medicine.getPrice()
                        ),
                SwingConstants.CENTER
        );

        JLabel stock = new JLabel(
                "Stock: "
                        + medicine.getStock(),
                SwingConstants.CENTER
        );

        JLabel slot = new JLabel(
                "Slot: "
                        + medicine.getSlotNumber(),
                SwingConstants.CENTER
        );

        JButton selectButton =
                new JButton("Select");

        // =================================================
        // SELECT MEDICINE
        // =================================================

        selectButton.addActionListener(e -> {

            String input =
                    JOptionPane.showInputDialog(
                            this,
                            "Enter quantity for "
                                    + medicine.getMedicineName()
                    );

            if (input == null) {
                return;
            }

            try {

                int quantity =
                        Integer.parseInt(
                                input.trim()
                        );

                // -----------------------------------------
                // QUANTITY CHECK
                // -----------------------------------------

                if (quantity <= 0) {

                    JOptionPane.showMessageDialog(
                            this,
                            "Quantity must be greater than 0.",
                            "Invalid Quantity",
                            JOptionPane.WARNING_MESSAGE
                    );

                    return;
                }

                // -----------------------------------------
                // PRESCRIPTION CHECK
                // -----------------------------------------

                if (medicine.isPrescriptionRequired()) {

                    JTextField prescriptionField =
                            new JTextField();

                    JPanel prescriptionPanel =
                            new JPanel(
                                    new GridLayout(
                                            2,
                                            1,
                                            5,
                                            5
                                    )
                            );

                    prescriptionPanel.add(
                            new JLabel(
                                    "Enter Prescription ID:"
                            )
                    );

                    prescriptionPanel.add(
                            prescriptionField
                    );

                    int result =
                            JOptionPane.showConfirmDialog(
                                    this,
                                    prescriptionPanel,
                                    "Prescription Verification",
                                    JOptionPane.OK_CANCEL_OPTION,
                                    JOptionPane.PLAIN_MESSAGE
                            );

                    if (result !=
                            JOptionPane.OK_OPTION) {

                        return;
                    }

                    String prescriptionId =
                            prescriptionField
                                    .getText()
                                    .trim();

                    if (prescriptionId.isEmpty()) {

                        JOptionPane.showMessageDialog(
                                this,
                                "Prescription ID is required.",
                                "Verification Failed",
                                JOptionPane.WARNING_MESSAGE
                        );

                        return;
                    }

                    JOptionPane.showMessageDialog(
                            this,
                            "Prescription ID accepted."
                    );
                }

                // -----------------------------------------
                // CHECK STOCK AND EXPIRY
                // -----------------------------------------

                if (!medicineService.isAvailable(
                        medicine,
                        quantity
                )) {

                    JOptionPane.showMessageDialog(
                            this,
                            "Medicine is not available "
                                    + "for that quantity.",
                            "Unavailable",
                            JOptionPane.WARNING_MESSAGE
                    );

                    return;
                }

                // -----------------------------------------
                // CALCULATE TOTAL
                // -----------------------------------------

                double total =
                        medicine.getPrice()
                                * quantity;

                // -----------------------------------------
                // PAYMENT
                // -----------------------------------------

                PaymentFrame paymentFrame =
                        new PaymentFrame(
                                this,
                                total
                        );

                paymentFrame.setVisible(true);

                if (!paymentFrame.isPaymentSuccessful()) {

                    JOptionPane.showMessageDialog(
                            this,
                            "Payment was not completed.",
                            "Payment Failed",
                            JOptionPane.WARNING_MESSAGE
                    );

                    return;
                }

                // -----------------------------------------
                // DISPENSE MEDICINE
                // -----------------------------------------

                boolean dispensingSuccessful =
                        hardwareController
                                .dispenseMedicine(
                                        medicine.getSlotNumber(),
                                        quantity
                                );

                if (!dispensingSuccessful) {

                    JOptionPane.showMessageDialog(
                            this,
                            "Payment successful, "
                                    + "but medicine dispensing failed.",
                            "Dispensing Error",
                            JOptionPane.ERROR_MESSAGE
                    );

                    return;
                }

                // -----------------------------------------
                // UPDATE STOCK
                // -----------------------------------------

                dao.MedicineDAO medicineDAO =
                        new dao.MedicineDAO();

                boolean stockUpdated =
                        medicineDAO.updateStock(
                                medicine.getMedicineId(),
                                quantity
                        );

                if (!stockUpdated) {

                    JOptionPane.showMessageDialog(
                            this,
                            "Medicine was dispensed, "
                                    + "but stock update failed.",
                            "Stock Error",
                            JOptionPane.ERROR_MESSAGE
                    );

                    return;
                }

                // -----------------------------------------
                // SAVE TRANSACTION
                // -----------------------------------------

                TransactionDAO transactionDAO =
                        new TransactionDAO();

                int transactionId =
                        transactionDAO.saveTransaction(
                                medicine.getMedicineId(),
                                medicine.getMedicineName(),
                                quantity,
                                total
                        );

                if (transactionId == -1) {

                    JOptionPane.showMessageDialog(
                            this,
                            "Medicine was dispensed and stock "
                                    + "was updated, but transaction "
                                    + "could not be saved.",
                            "Transaction Error",
                            JOptionPane.ERROR_MESSAGE
                    );

                    return;
                }

                // -----------------------------------------
                // SUCCESS MESSAGE
                // -----------------------------------------

                JOptionPane.showMessageDialog(
                        this,
                        "PURCHASE SUCCESSFUL!\n\n"
                                + "Medicine: "
                                + medicine.getMedicineName()
                                + "\nQuantity: "
                                + quantity
                                + "\nTotal: ₹"
                                + String.format(
                                        "%.2f",
                                        total
                                )
                                + "\n\n"
                                + "Medicine dispensed successfully!"
                );

                // -----------------------------------------
                // RECEIPT
                // -----------------------------------------

                new ReceiptFrame(
                        medicine.getMedicineName(),
                        quantity,
                        medicine.getPrice(),
                        total,
                        transactionId
                );

                // -----------------------------------------
                // REFRESH MEDICINES
                // -----------------------------------------

                loadMedicines();

            } catch (NumberFormatException ex) {

                JOptionPane.showMessageDialog(
                        this,
                        "Please enter a valid number.",
                        "Invalid Quantity",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        card.add(name);
        card.add(price);
        card.add(stock);
        card.add(slot);
        card.add(selectButton);

        medicinePanel.add(card);
    }

    // =====================================================
    // SEARCH
    // =====================================================

    private void searchMedicines() {

        String searchText =
                searchField.getText()
                        .trim()
                        .toLowerCase();

        medicinePanel.removeAll();

        List<Medicine> medicines =
                medicineService.getAvailableMedicines();

        for (Medicine medicine : medicines) {

            if (medicine.getMedicineName()
                    .toLowerCase()
                    .contains(searchText)) {

                addMedicineCard(medicine);
            }
        }

        medicinePanel.revalidate();
        medicinePanel.repaint();
    }

    // =====================================================
    // MAIN
    // =====================================================

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() ->
                new MedicineFrame()
        );
    }
}