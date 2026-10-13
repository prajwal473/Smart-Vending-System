import javax.swing.SwingUtilities;
import ui.MedicineFrame;

public class Main {

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            new MedicineFrame();
        });

    }
}