package model;

import java.time.LocalDate;

public class Medicine {

    private int medicineId;
    private String medicineName;
    private String category;
    private double price;
    private int stock;
    private int slotNumber;
    private LocalDate expiryDate;
    private boolean prescriptionRequired;

    public Medicine(int medicineId, String medicineName, String category,
                    double price, int stock, int slotNumber,
                    LocalDate expiryDate, boolean prescriptionRequired) {

        this.medicineId = medicineId;
        this.medicineName = medicineName;
        this.category = category;
        this.price = price;
        this.stock = stock;
        this.slotNumber = slotNumber;
        this.expiryDate = expiryDate;
        this.prescriptionRequired = prescriptionRequired;
    }

    public int getMedicineId() {
        return medicineId;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public String getCategory() {
        return category;
    }

    public double getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public int getSlotNumber() {
        return slotNumber;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public boolean isPrescriptionRequired() {
        return prescriptionRequired;
    }

    @Override
    public String toString() {
        return medicineId + " | " +
               medicineName + " | ₹" +
               price + " | Stock: " +
               stock + " | Slot: " +
               slotNumber;
    }
}
