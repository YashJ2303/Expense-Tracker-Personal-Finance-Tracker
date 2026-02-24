CREATE DATABASE IF NOT EXISTS expensetracker;

USE expensetracker;

CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS expenses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    category VARCHAR(50),
    amount DECIMAL(10, 2) NOT NULL CHECK (amount > 0),
    date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (username) REFERENCES users(username)
);

-- Note: Other tables (recurring_expenses, reminders) are handled via Java code in ExpenseTracker.java
