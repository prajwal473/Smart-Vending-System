package service;

import dao.MedicineDAO;
import java.time.LocalDate;
import java.util.List;
import model.Medicine;

public class MedicineService {

    private MedicineDAO medicineDAO;

    public MedicineService() {
        medicineDAO = new MedicineDAO();
    }

    public List<Medicine> getAvailableMedicines() {

        List<Medicine> medicines = medicineDAO.getAllMedicines();

        medicines.removeIf(medicine ->
                medicine.getStock() <= 0 ||
                medicine.getExpiryDate().isBefore(LocalDate.now())
        );

        return medicines;
    }

    public boolean isAvailable(Medicine medicine, int quantity) {

        if (medicine == null) {
            return false;
        }

        if (quantity <= 0) {
            return false;
        }

        if (medicine.getStock() < quantity) {
            return false;
        }

        if (medicine.getExpiryDate().isBefore(LocalDate.now())) {
            return false;
        }

        return true;
    }
}