package hardware;

public class SimulatorHardware implements HardwareController {

    @Override
    public boolean dispenseMedicine(int slot, int quantity) {

        System.out.println("Connecting to vending machine...");
        System.out.println("Slot selected: " + slot);
        System.out.println("Quantity: " + quantity);

        System.out.println("Motor starting...");

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Medicine detected by sensor.");
        System.out.println("Motor stopped.");

        System.out.println("Medicine dispensed successfully!");

        return true;
    }
}