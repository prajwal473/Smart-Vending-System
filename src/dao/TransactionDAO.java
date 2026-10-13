package dao;

import database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class TransactionDAO {

    public int saveTransaction(
            int medicineId,
            String medicineName,
            int quantity,
            double totalAmount) {

        String sql =
                "INSERT INTO transactions " +
                "(medicine_id, medicine_name, quantity, total_amount) " +
                "VALUES (?, ?, ?, ?)";

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql,
                                Statement.RETURN_GENERATED_KEYS
                        )
        ) {

            statement.setInt(1, medicineId);
            statement.setString(2, medicineName);
            statement.setInt(3, quantity);
            statement.setDouble(4, totalAmount);

            int rowsInserted =
                    statement.executeUpdate();

            if (rowsInserted == 0) {
                return -1;
            }

            try (ResultSet generatedKeys =
                         statement.getGeneratedKeys()) {

                if (generatedKeys.next()) {

                    return generatedKeys.getInt(1);
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

        return -1;
    }

    public void getAllTransactions() {

        String sql =
                "SELECT * FROM transactions " +
                "ORDER BY transaction_date DESC";

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet result =
                        statement.executeQuery()
        ) {

            while (result.next()) {

                System.out.println(
                        "ID: "
                                + result.getInt(
                                        "transaction_id"
                                )
                                + " | Medicine: "
                                + result.getString(
                                        "medicine_name"
                                )
                                + " | Quantity: "
                                + result.getInt(
                                        "quantity"
                                )
                                + " | Amount: ₹"
                                + result.getDouble(
                                        "total_amount"
                                )
                                + " | Date: "
                                + result.getTimestamp(
                                        "transaction_date"
                                )
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}