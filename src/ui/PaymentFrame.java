package ui;

import java.awt.*;
import javax.swing.*;

public class PaymentFrame extends JDialog {

    private boolean paymentSuccessful = false;

    public PaymentFrame(
            JFrame parent,
            double amount) {

        super(parent, "Payment", true);

        setSize(450, 350);
        setLocationRelativeTo(parent);

        JLabel title = new JLabel(
                "PAYMENT",
                SwingConstants.CENTER
        );

        title.setFont(
                new Font("Arial", Font.BOLD, 26)
        );

        add(title, BorderLayout.NORTH);

        JPanel panel = new JPanel(
                new GridLayout(3, 1, 15, 15)
        );

        panel.setBorder(
                BorderFactory.createEmptyBorder(
                        40, 60, 40, 60
                )
        );

        JLabel amountLabel = new JLabel(
                "Amount to Pay: ₹"
                        + String.format("%.2f", amount),
                SwingConstants.CENTER
        );

        amountLabel.setFont(
                new Font("Arial", Font.BOLD, 20)
        );

        panel.add(amountLabel);

        JButton payButton =
                new JButton("PAY NOW");

        payButton.setFont(
                new Font("Arial", Font.BOLD, 18)
        );

        panel.add(payButton);

        JLabel statusLabel =
                new JLabel(
                        "Ready for payment",
                        SwingConstants.CENTER
                );

        panel.add(statusLabel);

        add(panel, BorderLayout.CENTER);

        payButton.addActionListener(e -> {

            payButton.setEnabled(false);

            statusLabel.setText(
                    "Processing payment..."
            );

            Timer timer = new Timer(
                    1500,
                    event -> {

                        paymentSuccessful = true;

                        statusLabel.setText(
                                "PAYMENT SUCCESSFUL"
                        );

                        JOptionPane.showMessageDialog(
                                this,
                                "Payment Successful!\n\n"
                                        + "Amount Paid: ₹"
                                        + String.format(
                                                "%.2f",
                                                amount
                                        ),
                                "Payment",
                                JOptionPane.INFORMATION_MESSAGE
                        );

                        dispose();
                    }
            );

            timer.setRepeats(false);
            timer.start();
        });
    }

    public boolean isPaymentSuccessful() {

        return paymentSuccessful;
    }

    public static void main(String[] args) {

        JFrame frame = new JFrame();

        frame.setDefaultCloseOperation(
                JFrame.EXIT_ON_CLOSE
        );

        frame.setSize(300, 200);

        frame.setLocationRelativeTo(null);

        new PaymentFrame(
                frame,
                100.00
        ).setVisible(true);
    }
}