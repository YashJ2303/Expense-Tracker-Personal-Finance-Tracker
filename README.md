# 💰 Expense Tracker — Premium Finance Manager

A modern, high-performance Expense Tracking application with a premium glassmorphism UI. Built with a robust Java backend and a sleek, interactive frontend.

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-17%2B-orange)
![MySQL](https://img.shields.io/badge/MySQL-8.0%2B-blue)

## 📖 Table of Contents
- [✨ Features](#-features)
- [🛠️ Tech Stack](#️-tech-stack)
- [🏗️ Architecture](#️-architecture)
- [🚀 Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Database Setup](#database-setup)
  - [Installation & Run](#installation--run)
- [⌨️ Keyboard Shortcuts](#️-keyboard-shortcuts)
- [🤝 Contributing](#-contributing)

## ✨ Features

-   **💎 Premium UI**: Modern glassmorphism design with semi-transparent elements and vibrant gradients.
-   **📊 Dynamic Dashboard**: Real-time overview of monthly spending, transaction counts, and category breakdowns.
-   **📈 Advanced Analytics**: Interactive Chart.js visualizations and a longitudinal spending heatmap.
-   **🎯 Smart Budgets**: Set financial goals per category with real-time threshold alerts.
-   **🤖 AI Predictions**: Data-driven forecasting for future spending based on historical local data.
-   **🔄 Recurring Expenses**: Automated tracking for subscriptions, utilities, and monthly bills.
-   **🔔 Smart Reminders**: Integrated system for due dates and financial milestones.
-   **🌗 Dual Theme**: Native Dark and Light mode support with a custom SVG icon system.

## 🛠️ Tech Stack

-   **Backend**: Java 17+ (HttpServer), JDBC
-   **Database**: MySQL 8.0+
-   **Frontend**: Vanilla HTML5, CSS3 (Glassmorphism), JavaScript (ES6+)
-   **Charting**: Chart.js
-   **Icons**: Custom Optimized SVG Icon Sprite System

## 🏗️ Architecture

The application follows a clean modular architecture:
- **`api`**: Lightweight HTTP Server implementation handling RESTful endpoints.
- **`service`**: Business logic layer managing transaction processing and authentication.
- **`model`**: Data structures representing Expenses, Users, and Budgets.
- **`cli`**: Supplementary command-line interface for direct database interaction.
- **`web`**: Decoupled frontend assets served via the Java backend.

## 🚀 Getting Started

### Prerequisites

- **Java Development Kit (JDK) 23** or higher
- **MySQL Server**
- A modern web browser (Chrome, Firefox, Edge, Safari)

### Database Setup

1.  Create a MySQL database:
    ```sql
    CREATE DATABASE expense_tracker;
    ```
2.  Import the provided schema:
    ```bash
    mysql -u root -p expense_tracker < schema.sql
    ```
3.  Configure credentials:
    -   Rename `config/db.properties.example` to `config/db.properties`.
    -   Update with your local MySQL `user` and `password`.

### Installation & Run

1. **Clone the repository**:
   ```bash
   git clone https://github.com/YashJ2303/Expense-Tracker-Personal-Finance-Tracker.git
   cd Expense-Tracker-Personal-Finance-Tracker
   ```

2. **Compile the project**:
   ```powershell
   # Create build directory
   mkdir build -ErrorAction SilentlyContinue

   # Compile source files
   javac -d build -cp "lib/mysql-connector-j-9.2.0.jar" src\api\ExpenseAPI.java src\cli\ExpenseTracker.java src\model\Expense.java src\model\User.java src\security\PasswordHasher.java src\service\AuthService.java src\service\ExpenseService.java
   ```

3. **Run the Application**:
   ```powershell
   # Start Web Server
   java -cp "build;lib/mysql-connector-j-9.2.0.jar" api.ExpenseAPI
   ```

4. **Access the Dashboard**:
   Navigate to [http://localhost:8080](http://localhost:8080) in your browser.

## ⌨️ Keyboard Shortcuts

| Key | Action |
| :--- | :--- |
| `N` | Open "Add Expense" Modal |
| `1` | Home Dashboard |
| `2` | Transaction History |
| `3` | Visual Trends |
| `4` | Detailed Reports |
| `5` | Budget Manager |
| `T` | Toggle UI Theme |

---
*Developed by [YashJ2303](https://github.com/YashJ2303)*
