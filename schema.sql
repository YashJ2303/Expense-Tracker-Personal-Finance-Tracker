-- Expense Tracker Database Schema

CREATE DATABASE IF NOT EXISTS expense_tracker;
USE expense_tracker;

-- Users Table
CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(50) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Categories Table
CREATE TABLE IF NOT EXISTS categories (
    name VARCHAR(50) PRIMARY KEY
);

-- Initial Categories
INSERT IGNORE INTO categories (name) VALUES 
('Food'), ('Transport'), ('Shopping'), ('Utilities'), ('Entertainment'), ('Health'), ('Education'), ('Others');

-- Expenses Table
CREATE TABLE IF NOT EXISTS expenses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50),
    category VARCHAR(50),
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'INR',
    receipt_path VARCHAR(255),
    date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (username) REFERENCES users(username),
    FOREIGN KEY (category) REFERENCES categories(name)
);

-- Budgets Table
CREATE TABLE IF NOT EXISTS budgets (
    username VARCHAR(50),
    category VARCHAR(50),
    monthly_limit DECIMAL(15, 2) NOT NULL,
    PRIMARY KEY (username, category),
    FOREIGN KEY (username) REFERENCES users(username),
    FOREIGN KEY (category) REFERENCES categories(name)
);

-- Recurring Expenses Table
CREATE TABLE IF NOT EXISTS recurring_expenses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50),
    category VARCHAR(50),
    amount DECIMAL(15, 2) NOT NULL,
    interval_type ENUM('daily', 'weekly', 'monthly') NOT NULL,
    start_date DATE NOT NULL,
    last_applied_date DATE,
    FOREIGN KEY (username) REFERENCES users(username),
    FOREIGN KEY (category) REFERENCES categories(name)
);

-- Reminders Table
CREATE TABLE IF NOT EXISTS reminders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50),
    title VARCHAR(255) NOT NULL,
    due_date DATE NOT NULL,
    notes TEXT,
    is_completed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (username) REFERENCES users(username)
);
