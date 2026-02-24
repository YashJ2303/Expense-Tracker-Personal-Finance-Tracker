# 💰 Expense Tracker — Premium Finance Manager

A modern, high-performance Expense Tracking application with a premium glassmorphism UI. Built with a robust Java backend and a sleek, interactive frontend.

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-17%2B-orange)
![MySQL](https://img.shields.io/badge/MySQL-8.0%2B-blue)

## ✨ Features

-   **Dashboard**: Overview of monthly spending, total transactions, and top categories.
-   **Analytics & Trends**: Interactive charts (Chart.js) and a daily spending heatmap.
-   **Smart Budgets**: Set monthly limits and receive visual alerts when approaching thresholds.
-   **AI Predictions**: Forecast next month's spending based on historical data.
-   **Recurring Expenses**: Automate tracking for subscriptions and bills.
-   **Reminders**: Stay on top of due dates with a dedicated reminders system.
-   **Dual Theme**: Seamless Dark and Light mode support with a custom SVG icon system.
-   **Keyboard Shortcuts**: Fast navigation for power users (N: New, 1-5: Pages, T: Theme).

## 🛠️ Tech Stack

-   **Backend**: Java (HttpServer), JDBC, MySQL
-   **Frontend**: Vanilla HTML5, CSS3 (Glassmorphism), JavaScript (ES6+)
-   **Charting**: Chart.js
-   **Icons**: Custom SVG Icon Sprite System

## 🚀 Getting Started

### Prerequisites

-   Java 17 or higher
-   MySQL Server
-   A modern web browser

### Database Setup

1.  Create a MySQL database named `expense_tracker`.
2.  Import the database schema:
    ```bash
    mysql -u root -p expense_tracker < schema.sql
    ```
3.  Configure your credentials:
    -   Copy `config/db.properties.example` to `config/db.properties`.
    -   Update the file with your local MySQL username and password.

### Installation

1.  Clone the repository:
    ```bash
    git clone https://github.com/yourusername/expense-tracker-java.git
    cd expense-tracker-java
    ```

2.  Compile the project:
    ```bash
    # On Windows (from project root)
    dir src\*.java /s /b > sources.txt
    javac -d build -cp "lib/*" @sources.txt
    del sources.txt
    ```

3.  Run the application:
    ```bash
    # Run API server (Web UI)
    java -cp "build;lib/mysql-connector-j-9.2.0.jar" api.ExpenseAPI

    # Run CLI interface
    java -cp "build;lib/mysql-connector-j-9.2.0.jar" cli.ExpenseTracker
    ```

4.  Open your browser and navigate to:
    ```
    http://localhost:8080
    ```

## ⌨️ Keyboard Shortcuts

| Key | Action |
| :--- | :--- |
| `N` | Open "Add Expense" Modal |
| `1` | Dashboard |
| `2` | Expenses List |
| `3` | Trends & Heatmap |
| `4` | Reports |
| `5` | Budgets |
| `T` | Toggle Dark/Light Mode |
| `?` | Show Shortcuts |

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
