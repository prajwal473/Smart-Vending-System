package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String URL =
            "jdbc:mysql://localhost:3306/medicine_vending";

    private static final String USER = "root";

    private static final String PASSWORD =
            "Prajwal@2007";

    public static Connection getConnection() throws SQLException {

        Connection connection =
                DriverManager.getConnection(
                        URL,
                        USER,
                        PASSWORD
                );

      

        return connection;
    }
}