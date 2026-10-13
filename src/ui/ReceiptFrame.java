package ui;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.*;

public class ReceiptFrame extends JFrame {

    public ReceiptFrame(
            String medicineName,
            int quantity,
            double price,
            double total,
            int transactionId) {

        setTitle("Purchase Receipt");
        setSize(500, 500);

        setDefaultCloseOperation(
                JFrame.DISPOSE_ON_CLOSE
        );

        setLocationRelativeTo(null);

        // ---------------- TITLE ----------------

        JLabel title = new JLabel(
                "PURCHASE RECEIPT",
                SwingConstants.CENTER
        );

        title.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        26
                )
        );

        add(title, BorderLayout.NORTH);

        // ---------------- RECEIPT ----------------

        JPanel receiptPanel = new JPanel();

        receiptPanel.setLayout(
                new GridLayout(
                        0,
                        1,
                        10,
                        10
                )
        );

        receiptPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        30,
                        50,
                        20,
                        50
                )
        );

        JLabel transactionLabel =
                new JLabel(
                        "Transaction ID: "
                                + transactionId
                );

        JLabel medicineLabel =
                new JLabel(
                        "Medicine: "
                                + medicineName
                );

        JLabel quantityLabel =
                new JLabel(
                        "Quantity: "
                                + quantity
                );

        JLabel priceLabel =
                new JLabel(
                        "Price per item: ₹"
                                + String.format(
                                        "%.2f",
                                        price
                                )
                );

        JLabel totalLabel =
                new JLabel(
                        "Total Amount: ₹"
                                + String.format(
                                        "%.2f",
                                        total
                                )
                );

        JLabel paymentLabel =
                new JLabel(
                        "Payment Status: SUCCESS"
                );

        JLabel dispensingLabel =
                new JLabel(
                        "Dispensing Status: SUCCESS"
                );

        String dateTime =
                LocalDateTime.now()
                        .format(
                                DateTimeFormatter.ofPattern(
                                        "dd-MM-yyyy HH:mm:ss"
                                )
                        );

        JLabel dateLabel =
                new JLabel(
                        "Date: "
                                + dateTime
                );

        receiptPanel.add(
                transactionLabel
        );

        receiptPanel.add(
                medicineLabel
        );

        receiptPanel.add(
                quantityLabel
        );

        receiptPanel.add(
                priceLabel
        );

        receiptPanel.add(
                totalLabel
        );

        receiptPanel.add(
                paymentLabel
        );

        receiptPanel.add(
                dispensingLabel
        );

        receiptPanel.add(
                dateLabel
        );

        add(
                receiptPanel,
                BorderLayout.CENTER
        );

        // ---------------- CLOSE BUTTON ----------------

        JButton closeButton =
                new JButton("CLOSE");

        closeButton.addActionListener(e ->
                dispose()
        );

        add(
                closeButton,
                BorderLayout.SOUTH
        );

        setVisible(true);
    }
}
