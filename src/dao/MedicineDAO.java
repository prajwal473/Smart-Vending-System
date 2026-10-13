package dao;

import database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import model.Medicine;

public class MedicineDAO {

    public List<Medicine> getAllMedicines() {

        List<Medicine> medicines = new ArrayList<>();

        String sql = "SELECT * FROM medicines";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {

                Medicine medicine = new Medicine(
                    result.getInt("medicine_id"),
                    result.getString("medicine_name"),
                    result.getString("category"),
                    result.getDouble("price"),
                    result.getInt("stock"),
                    result.getInt("slot_number"),
                    result.getDate("expiry_date").toLocalDate(),
                    result.getBoolean("prescription_required")
                );

                medicines.add(medicine);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return medicines;
    }
    public boolean updateStock(int medicineId, int quantity) {

    String sql =
            "UPDATE medicines " +
            "SET stock = stock - ? " +
            "WHERE medicine_id = ? " +
            "AND stock >= ?";

    try (Connection connection = DatabaseConnection.getConnection();
         PreparedStatement statement =
                 connection.prepareStatement(sql)) {

        statement.setInt(1, quantity);
        statement.setInt(2, medicineId);
        statement.setInt(3, quantity);

        int rowsUpdated = statement.executeUpdate();

        return rowsUpdated > 0;

    } catch (Exception e) {
        e.printStackTrace();
        return false;
    }
}
public boolean addStock(int medicineId, int quantity) {

    String sql =
            "UPDATE medicines " +
            "SET stock = stock + ? " +
            "WHERE medicine_id = ?";

    try (Connection connection =
                 DatabaseConnection.getConnection();
         PreparedStatement statement =
                 connection.prepareStatement(sql)) {

        statement.setInt(1, quantity);
        statement.setInt(2, medicineId);

        int rowsUpdated = statement.executeUpdate();

        return rowsUpdated > 0;

    } catch (Exception e) {
        e.printStackTrace();
        return false;
    }
}
public boolean addMedicine(
        String name,
        String category,
        double price,
        int stock,
        int slot,
        String expiry,
        boolean prescriptionRequired) {

    String sql =
            "INSERT INTO medicines " +
            "(medicine_name, category, price, stock, " +
            "slot_number, expiry_date, prescription_required) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

    try (Connection connection =
                 DatabaseConnection.getConnection();
         PreparedStatement statement =
                 connection.prepareStatement(sql)) {

        statement.setString(1, name);
        statement.setString(2, category);
        statement.setDouble(3, price);
        statement.setInt(4, stock);
        statement.setInt(5, slot);

        // Convert text date to SQL date
        statement.setDate(
                6,
                java.sql.Date.valueOf(expiry)
        );

        // For now, new medicines are OTC
        statement.setBoolean(7, prescriptionRequired);

        int rowsInserted =
                statement.executeUpdate();

        return rowsInserted > 0;

    } catch (Exception e) {

        e.printStackTrace();

        return false;
    }
}
}