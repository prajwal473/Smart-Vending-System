package ui;

import javax.swing.*;
import java.awt.*;

public class AdminLoginFrame extends JFrame {

    private JTextField usernameField;
    private JPasswordField passwordField;

    public AdminLoginFrame() {

        setTitle("Admin Login");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        // ---------------- TITLE ----------------

        JLabel title = new JLabel(
                "ADMIN LOGIN",
                SwingConstants.CENTER
        );

        title.setFont(
                new Font("Arial", Font.BOLD, 24)
        );

        add(title, BorderLayout.NORTH);

        // ---------------- FORM PANEL ----------------

        JPanel panel = new JPanel(
                new GridLayout(3, 2, 10, 10)
        );

        panel.setBorder(
                BorderFactory.createEmptyBorder(
                        40, 40, 40, 40
                )
        );

        // Username

        panel.add(
                new JLabel("Username:")
        );

        usernameField = new JTextField();

        panel.add(usernameField);

        // Password

        panel.add(
                new JLabel("Password:")
        );

        passwordField = new JPasswordField();

        panel.add(passwordField);

        // Login button

        JButton loginButton =
                new JButton("Login");

        panel.add(loginButton);

        add(panel, BorderLayout.CENTER);

        // ---------------- LOGIN ACTION ----------------

        loginButton.addActionListener(e ->
                login()
        );

        setVisible(true);
    }

    // =====================================================
    // LOGIN
    // =====================================================

    private void login() {

        String username =
                usernameField.getText().trim();

        String password =
                new String(
                        passwordField.getPassword()
                );

        // Temporary project login
        // We will connect this to MySQL later.

        if (username.equals("admin")
                && password.equals("admin123")) {

            JOptionPane.showMessageDialog(
                    this,
                    "Admin Login Successful!"
            );

            new AdminDashboardFrame();

            dispose();

        } else {

            JOptionPane.showMessageDialog(
                    this,
                    "Invalid username or password.",
                    "Login Failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // =====================================================
    // MAIN
    // =====================================================

    public static void main(String[] args) {

        new AdminLoginFrame();
    }
}
