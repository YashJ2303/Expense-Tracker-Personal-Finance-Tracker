package service;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DatabaseManager {
    private static String dbUrl;
    private static String dbUser;
    private static String dbPass;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("JDBC Driver not found. Please add the connector JAR to your classpath.");
        }
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("config/db.properties")) {
            props.load(fis);
            dbUrl = props.getProperty("db.url");
            dbUser = props.getProperty("db.user");
            dbPass = props.getProperty("db.password");
        } catch (IOException e) {
            System.err.println("Fatal: Could not load config/db.properties. Falling back to defaults.");
            dbUrl = "jdbc:mysql://localhost:3306/expensetracker?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
            dbUser = "root";
            dbPass = "Yash@mysql23";
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPass);
    }

    public static void initializeDatabase() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Users table (Must be first for FK constraints)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(50) UNIQUE NOT NULL,
                        password_hash VARCHAR(255) NOT NULL,
                        salt VARCHAR(255) NOT NULL
                    )""");

            // Expenses table
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS expenses (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(50) NOT NULL,
                        category VARCHAR(100),
                        amount DECIMAL(10, 2) NOT NULL CHECK (amount > 0),
                        currency VARCHAR(3) DEFAULT 'INR',
                        receipt_path VARCHAR(255),
                        date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_username (username), INDEX idx_date (date),
                        FOREIGN KEY (username) REFERENCES users(username)
                    )""");

            // Migrate existing expenses table: add missing columns
            try {
                stmt.execute("ALTER TABLE expenses ADD COLUMN currency VARCHAR(3) DEFAULT 'INR'");
            } catch (SQLException ignored) {
                /* Column already exists */ }
            try {
                stmt.execute("ALTER TABLE expenses ADD COLUMN receipt_path VARCHAR(255)");
            } catch (SQLException ignored) {
                /* Column already exists */ }

            // Recurring expenses table
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS recurring_expenses (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(100) NOT NULL,
                        description VARCHAR(255) NOT NULL,
                        amount DECIMAL(10, 2) NOT NULL CHECK (amount > 0),
                        category VARCHAR(100) NOT NULL,
                        interval_type ENUM('daily', 'weekly', 'monthly') NOT NULL,
                        start_date DATE NOT NULL,
                        last_applied_date DATE,
                        INDEX idx_rec_username (username)
                    )""");

            // Reminders table
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS reminders (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(100) NOT NULL,
                        title VARCHAR(255) NOT NULL,
                        due_date DATE NOT NULL,
                        notes TEXT,
                        INDEX idx_rem_username (username),
                        INDEX idx_rem_due_date (due_date)
                    )""");

            // Categories table
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(50) UNIQUE NOT NULL
                    )""");

            // Budgets table
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS budgets (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(50) NOT NULL,
                        category VARCHAR(100) NOT NULL,
                        monthly_limit DECIMAL(10, 2) NOT NULL CHECK (monthly_limit > 0),
                        UNIQUE KEY uq_user_cat (username, category),
                        FOREIGN KEY (username) REFERENCES users(username)
                    )""");

            // Seed default categories if empty
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM categories")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute(
                            "INSERT INTO categories (name) VALUES ('Food'), ('Transport'), ('Rent'), ('Entertainment'), ('Health'), ('Other')");
                }
            }
        }
    }
}
