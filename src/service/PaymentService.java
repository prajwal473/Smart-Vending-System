package service;

public class PaymentService {

    public boolean processPayment(double amount) {

        System.out.println("Processing payment...");
        System.out.println("Amount: ₹" + amount);

        // Simulated payment
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Payment successful!");

        return true;
    }
}
