package cli;

import model.Expense;
import service.DatabaseManager;
import service.ExpenseService;
import security.SecurityUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

public class ExpenseTracker {

    private static final String BUDGET_FILE_PREFIX = "budget_";
    private static final String BUDGET_FILE_SUFFIX = ".txt";
    private static final DateTimeFormatter DATE_ONLY_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter YM_FMT = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final int REMINDER_DAYS_AHEAD = 5;
    private static final int PREDICTION_MONTHS = 3;

    private static String currentUser = null;
    private static BigDecimal monthlyBudget = BigDecimal.ZERO;

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            DatabaseManager.initializeDatabase(); // Initialize DB tables

            if (!performAuthentication(sc)) {
                System.out.println("Authentication failed/exited. Exiting.");
                return;
            }
            loadBudget();

            try (Connection conn = DatabaseManager.getConnection()) {
                System.out.println("Connected to Expense Tracker Database.");
                applyRecurringExpenses(conn);
                showDashboard(conn);
                runMainMenu(conn, sc);
            } catch (SQLException e) {
                System.err.println("DB Operation Error: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
        }
        System.out.println("\nExiting Expense Tracker. Goodbye!");
    }

    private static boolean performAuthentication(Scanner sc) {
        while (true) {
            System.out.println("\n--- Auth Menu ---\n1. Login\n2. Sign Up\n3. Exit");
            System.out.print("Choose: ");
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1":
                    if (login(sc))
                        return true;
                    break;
                case "2":
                    if (signUp(sc)) {
                        System.out.println("Sign up OK. Logged in.");
                        return true;
                    }
                    break;
                case "3":
                    return false;
                default:
                    System.out.println("Invalid option (1-3).");
                    break;
            }
        }
    }

    private static boolean login(Scanner sc) {
        String username = readNonEmptyString(sc, "Username: ");
        System.out.print("Password: ");
        String password = sc.nextLine();

        try (Connection conn = DatabaseManager.getConnection()) {
            String sql = "SELECT password_hash, salt FROM users WHERE username = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String hash = rs.getString("password_hash");
                    String salt = rs.getString("salt");
                    if (SecurityUtils.verifyPassword(password, salt, hash)) {
                        currentUser = username;
                        System.out.println("Login successful!");
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Login error: " + e.getMessage());
        }
        System.out.println("Invalid username or password.");
        return false;
    }

    private static boolean signUp(Scanner sc) {
        String username = readNonEmptyString(sc, "Choose username: ");
        System.out.print("Choose password: ");
        String password = sc.nextLine();

        String salt = SecurityUtils.generateSalt();
        String hash = SecurityUtils.hashPassword(password, salt);

        try (Connection conn = DatabaseManager.getConnection()) {
            String sql = "INSERT INTO users (username, password_hash, salt) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, hash);
                ps.setString(3, salt);
                ps.executeUpdate();
                currentUser = username;
                monthlyBudget = BigDecimal.ZERO;
                saveBudget();
                return true;
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                System.out.println("Username already taken.");
            } else {
                System.err.println("Signup error: " + e.getMessage());
            }
            return false;
        }
    }

    private static void runMainMenu(Connection conn, Scanner sc) {
        boolean exit = false;
        while (!exit) {
            printMainMenu();
            int option = readUserOption(sc, 1, 12);
            try {
                switch (option) {
                    case 1 -> addExpense(conn, sc);
                    case 2 -> viewExpenses(conn);
                    case 3 -> generateMonthlyReport(conn, sc);
                    case 4 -> deleteExpense(conn, sc);
                    case 5 -> setMonthlyBudget(sc);
                    case 6 -> manageRecurringExpenses(conn, sc);
                    case 7 -> manageReminders(conn, sc);
                    case 8 -> searchExpensesUI(conn, sc);
                    case 9 -> showDashboard(conn);
                    case 10 -> exportToCSV(conn, sc);
                    case 11 -> manageCategoriesUI(conn, sc);
                    case 12 -> {
                        saveBudget();
                        System.out.println("\nBudget saved. Goodbye " + currentUser + "!");
                        exit = true;
                    }
                }
                if (!exit && option != 9)
                    promptForEnter(sc);
            } catch (SQLException | RuntimeException e) {
                System.err.println("\nAn error occurred: " + e.getMessage());
                e.printStackTrace();
                promptForEnter(sc);
            }
        }
    }

    private static void addExpense(Connection conn, Scanner sc) throws SQLException {
        List<String> categories = ExpenseService.getCategories(conn);
        System.out.println("\nAvailable Categories: " + String.join(", ", categories));
        String category = readNonEmptyString(sc, "Enter category (or a new one): ");
        BigDecimal amount = readPositiveBigDecimal(sc, "Enter amount: ");
        if (addExpenseInternal(conn, category, amount, LocalDateTime.now())) {
            // Check if it's a new category and add it automatically if so?
            // For now, let's just add it if it doesn't exist to keep it simple.
            if (!categories.contains(category)) {
                ExpenseService.addCategory(conn, category);
            }
            System.out.println("Expense added!");
            checkBudgetExceeded(conn);
        } else {
            System.err.println("Failed to add expense.");
        }
    }

    private static boolean addExpenseInternal(Connection conn, String category, BigDecimal amount, LocalDateTime date)
            throws SQLException {
        String sql = "INSERT INTO expenses (username, category, amount, date) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentUser);
            ps.setString(2, category);
            ps.setBigDecimal(3, amount);
            ps.setTimestamp(4, Timestamp.valueOf(date));
            return ps.executeUpdate() > 0;
        }
    }

    private static void exportToCSV(Connection conn, Scanner sc) throws SQLException {
        String filename = readNonEmptyString(sc, "Enter filename to save CSV (e.g., expenses.csv): ");
        if (!filename.toLowerCase().endsWith(".csv"))
            filename += ".csv";

        String sql = "SELECT id, category, amount, date FROM expenses WHERE username = ? ORDER BY date DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
                BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            ps.setString(1, currentUser);
            ResultSet rs = ps.executeQuery();

            writer.write("ID,Category,Amount,Date");
            writer.newLine();

            int count = 0;
            while (rs.next()) {
                writer.write(String.format("%d,%s,%.2f,%s",
                        rs.getInt("id"),
                        rs.getString("category"),
                        rs.getBigDecimal("amount"),
                        rs.getTimestamp("date").toString()));
                writer.newLine();
                count++;
            }
            System.out.println("Successfully exported " + count + " records to " + filename);
        } catch (IOException e) {
            System.err.println("Error writing CSV file: " + e.getMessage());
        }
    }

    private static void viewExpenses(Connection conn) throws SQLException {
        String sql = "SELECT id, category, amount, date FROM expenses WHERE username = ? ORDER BY date DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentUser);
            ResultSet rs = ps.executeQuery();

            String[] headers = { "ID", "Category", "Amount", "Date" };
            List<String[]> rows = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;

            while (rs.next()) {
                BigDecimal amt = rs.getBigDecimal("amount");
                total = total.add(amt);
                rows.add(new String[] {
                        String.valueOf(rs.getInt("id")),
                        rs.getString("category"),
                        String.format("Rs. %.2f", amt),
                        rs.getTimestamp("date").toLocalDateTime()
                                .format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
                });
            }

            if (rows.isEmpty()) {
                System.out.println("\n--- Your Expenses ---\n(No expenses found)");
            } else {
                System.out.println("\n--- Your Expenses ---");
                TableUtils.printTable(headers, rows);
                System.out.printf("Total Expenses: Rs. %.2f\n", total);
            }
        }
    }

    private static void generateMonthlyReport(Connection conn, Scanner sc) throws SQLException {
        int year = LocalDate.now().getYear();
        Month month = readMonth(sc, "Enter month (1-12 or Jan-Dec) for report [Current Year: " + year + "]: ");
        if (month == null)
            return;
        YearMonth ym = YearMonth.of(year, month);
        System.out.printf("\n--- Monthly Report for %s ---\n", ym.format(YM_FMT));

        Map<String, BigDecimal> breakdown = ExpenseService.getCategoryBreakdown(conn, currentUser, month.getValue(),
                year);

        if (breakdown.isEmpty()) {
            System.out.println("No expenses this month.");
        } else {
            String[] headers = { "Category", "Amount" };
            List<String[]> rows = new ArrayList<>();
            BigDecimal monthlyTotal = BigDecimal.ZERO;

            for (Map.Entry<String, BigDecimal> entry : breakdown.entrySet()) {
                monthlyTotal = monthlyTotal.add(entry.getValue());
                rows.add(new String[] { entry.getKey(), String.format("Rs. %.2f", entry.getValue()) });
            }

            TableUtils.printTable(headers, rows);
            System.out.printf("Total Spent:           Rs. %.2f\n", monthlyTotal);

            if (monthlyBudget.compareTo(BigDecimal.ZERO) > 0) {
                System.out.printf("Budget Set:            Rs. %.2f\n", monthlyBudget);
                BigDecimal rem = monthlyBudget.subtract(monthlyTotal);
                System.out.printf("Remaining/Over Budget: Rs. %.2f %s\n", rem.abs(),
                        (rem.compareTo(BigDecimal.ZERO) < 0 ? "(Over!)" : ""));
            } else {
                System.out.println("Budget Set:            Not Set");
            }
        }
    }

    private static void deleteItem(Connection conn, Scanner sc, String itemType, String tableName,
            String viewMethodName) throws SQLException {
        int idToDelete = readInteger(sc, "\nEnter " + itemType + " ID to delete (0 to cancel): ", 0, Integer.MAX_VALUE);
        if (idToDelete == 0) {
            System.out.println("Deletion cancelled.");
            return;
        }

        String sqlCheck = "SELECT COUNT(*) FROM " + tableName + " WHERE id = ? AND username = ?";
        String sqlDelete = "DELETE FROM " + tableName + " WHERE id = ? AND username = ?";
        boolean ownsItem = false;

        try (PreparedStatement psCheck = conn.prepareStatement(sqlCheck)) {
            psCheck.setInt(1, idToDelete);
            psCheck.setString(2, currentUser);
            ResultSet rs = psCheck.executeQuery();
            if (rs.next() && rs.getInt(1) > 0)
                ownsItem = true;
        }

        if (!ownsItem) {
            System.out.println(itemType + " ID " + idToDelete + " not found or does not belong to you.");
            return;
        }

        try (PreparedStatement psDelete = conn.prepareStatement(sqlDelete)) {
            psDelete.setInt(1, idToDelete);
            psDelete.setString(2, currentUser);
            int rowsAffected = psDelete.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println(itemType + " ID " + idToDelete + " deleted successfully.");
            } else {
                System.err.println("Failed to delete " + itemType.toLowerCase() + " ID " + idToDelete + ".");
            }
        }
    }

    private static void deleteExpense(Connection conn, Scanner sc) throws SQLException {
        viewExpenses(conn);
        deleteItem(conn, sc, "Expense", "expenses", "viewExpenses");
    }

    private static void setMonthlyBudget(Scanner sc) {
        monthlyBudget = readNonNegativeBigDecimal(sc, "Enter new monthly budget (0 to unset): ");
        System.out.printf("Monthly budget %s.\n",
                (monthlyBudget.compareTo(BigDecimal.ZERO) > 0 ? "set to Rs. " + monthlyBudget.toPlainString()
                        : "unset"));
        saveBudget();
        System.out.println("Budget saved.");
    }

    private static void checkBudgetExceeded(Connection conn) throws SQLException {
        if (monthlyBudget.compareTo(BigDecimal.ZERO) <= 0)
            return;
        YearMonth currentMonth = YearMonth.now();
        BigDecimal totalThisMonth = ExpenseService.getTotalExpensesForMonth(conn, currentUser,
                currentMonth.getMonthValue(), currentMonth.getYear());
        if (totalThisMonth.compareTo(monthlyBudget) > 0) {
            System.out.println("\n\uD83D\uDEA8 BUDGET WARNING \uD83D\uDEA8");
            System.out.printf(" You have exceeded your monthly budget of Rs. %.2f!\n", monthlyBudget);
            System.out.printf(" Amount spent this month: Rs. %.2f (Over by Rs. %.2f)\n",
                    totalThisMonth, totalThisMonth.subtract(monthlyBudget));
            System.out.println("-".repeat(40));
        }
    }

    private static String getBudgetFilename() {
        if (currentUser == null)
            throw new IllegalStateException("Cannot get budget filename, user not logged in.");
        String safeUsername = currentUser.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return BUDGET_FILE_PREFIX + safeUsername + BUDGET_FILE_SUFFIX;
    }

    private static void saveBudget() {
        if (currentUser == null)
            return;
        try (BufferedWriter w = new BufferedWriter(new FileWriter(getBudgetFilename()))) {
            w.write(monthlyBudget.toPlainString());
        } catch (IOException | IllegalStateException e) {
            System.err.println("Error saving budget: " + e.getMessage());
        }
    }

    private static void loadBudget() {
        if (currentUser == null) {
            monthlyBudget = BigDecimal.ZERO;
            return;
        }
        File f = new File(getBudgetFilename());
        if (!f.exists()) {
            monthlyBudget = BigDecimal.ZERO;
            saveBudget();
            return;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line = r.readLine();
            monthlyBudget = (line != null && !line.trim().isEmpty()) ? new BigDecimal(line.trim()) : BigDecimal.ZERO;
        } catch (IOException | NumberFormatException | IllegalStateException e) {
            System.err.println("Error loading/parsing budget file '" + getBudgetFilename() + "': " + e.getMessage()
                    + ". Setting budget to 0.");
            monthlyBudget = BigDecimal.ZERO;
        }
    }

    private static void applyRecurringExpenses(Connection conn) throws SQLException {
        System.out.println("Checking for due recurring expenses...");
        String sqlSelect = "SELECT id, description, amount, category, interval_type, start_date, last_applied_date FROM recurring_expenses WHERE username = ?";
        String sqlUpdate = "UPDATE recurring_expenses SET last_applied_date = ? WHERE id = ?";
        LocalDate today = LocalDate.now();
        int appliedCount = 0;

        try (PreparedStatement psSelect = conn.prepareStatement(sqlSelect);
                PreparedStatement psUpdate = conn.prepareStatement(sqlUpdate)) {

            psSelect.setString(1, currentUser);
            ResultSet rs = psSelect.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                BigDecimal amount = rs.getBigDecimal("amount");
                String category = rs.getString("category");
                String intervalType = rs.getString("interval_type");
                LocalDate startDate = rs.getDate("start_date").toLocalDate();
                LocalDate lastAppliedDate = rs.getDate("last_applied_date") == null ? null
                        : rs.getDate("last_applied_date").toLocalDate();

                LocalDate nextDueDate = calculateNextDueDate(startDate, lastAppliedDate, intervalType);

                if (nextDueDate != null && !nextDueDate.isAfter(today)) {
                    System.out.printf("Applying recurring expense: %s (Rs. %.2f, %s)\n", rs.getString("description"),
                            amount, category);
                    if (addExpenseInternal(conn, category, amount, today.atStartOfDay())) {
                        psUpdate.setDate(1, java.sql.Date.valueOf(today));
                        psUpdate.setInt(2, id);
                        psUpdate.addBatch();
                        appliedCount++;
                    } else {
                        System.err.printf("Failed to add recurring expense entry for ID %d\n", id);
                    }
                }
            }
            rs.close();
            if (appliedCount > 0) {
                psUpdate.executeBatch();
            }
        }
        if (appliedCount > 0) {
            System.out.printf("%d recurring expense(s) applied.\n", appliedCount);
            checkBudgetExceeded(conn);
        } else {
            System.out.println("No recurring expenses were due.");
        }
    }

    private static LocalDate calculateNextDueDate(LocalDate startDate, LocalDate lastAppliedDate, String intervalType) {
        LocalDate referenceDate = (lastAppliedDate != null) ? lastAppliedDate : startDate.minusDays(1);
        return switch (intervalType.toLowerCase()) {
            case "daily" -> referenceDate.plusDays(1);
            case "weekly" -> referenceDate.plusWeeks(1);
            case "monthly" -> referenceDate.plusMonths(1);
            default -> {
                System.err.println("Warning: Unknown interval type '" + intervalType + "'");
                yield null;
            }
        };
    }

    private static void manageRecurringExpenses(Connection conn, Scanner sc) throws SQLException {
        while (true) {
            System.out.println("\n--- Manage Recurring Expenses ---\n1. View\n2. Add\n3. Delete\n4. Back");
            System.out.print("Choose option: ");
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1":
                    viewRecurringExpenses(conn);
                    break;
                case "2":
                    addRecurringExpense(conn, sc);
                    break;
                case "3":
                    deleteRecurringExpense(conn, sc);
                    break;
                case "4":
                    return;
                default:
                    System.out.println("Invalid choice (1-4).");
                    break;
            }
            if (!choice.equals("4"))
                promptForEnter(sc);
        }
    }

    private static void viewRecurringExpenses(Connection conn) throws SQLException {
        String sql = "SELECT id, description, amount, category, interval_type, start_date, last_applied_date FROM recurring_expenses WHERE username = ? ORDER BY start_date";
        System.out.println("\n--- Your Recurring Expenses ---");

        String[] headers = { "ID", "Description", "Amount", "Category", "Interval", "Start Date", "Last Applied" };
        List<String[]> rows = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentUser);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                LocalDate lastApplied = rs.getDate("last_applied_date") == null ? null
                        : rs.getDate("last_applied_date").toLocalDate();
                rows.add(new String[] {
                        String.valueOf(rs.getInt("id")),
                        rs.getString("description"),
                        String.format("Rs. %.2f", rs.getBigDecimal("amount")),
                        rs.getString("category"),
                        rs.getString("interval_type"),
                        rs.getDate("start_date").toLocalDate().format(DATE_ONLY_FMT),
                        lastApplied == null ? "Never" : lastApplied.format(DATE_ONLY_FMT)
                });
            }
        }

        if (rows.isEmpty()) {
            System.out.println("(No recurring expenses found)");
        } else {
            TableUtils.printTable(headers, rows);
        }
    }

    private static void addRecurringExpense(Connection conn, Scanner sc) throws SQLException {
        System.out.println("\n--- Add New Recurring Expense ---");
        String description = readNonEmptyString(sc, "Enter description: ");
        BigDecimal amount = readPositiveBigDecimal(sc, "Enter amount: ");
        String category = readNonEmptyString(sc, "Enter category: ");
        String interval = readIntervalType(sc);
        LocalDate startDate = readLocalDate(sc, "Enter start date (yyyy-MM-dd): ");

        String sql = "INSERT INTO recurring_expenses (username, description, amount, category, interval_type, start_date) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentUser);
            ps.setString(2, description);
            ps.setBigDecimal(3, amount);
            ps.setString(4, category);
            ps.setString(5, interval);
            ps.setDate(6, java.sql.Date.valueOf(startDate));
            if (ps.executeUpdate() > 0)
                System.out.println("Recurring expense added successfully!");
            else
                System.err.println("Failed to add recurring expense.");
        }
    }

    private static String readIntervalType(Scanner sc) {
        while (true) {
            System.out.print("Enter interval (daily, weekly, monthly): ");
            String input = sc.nextLine().trim().toLowerCase();
            if (List.of("daily", "weekly", "monthly").contains(input))
                return input;
            System.out.println("Invalid interval.");
        }
    }

    private static void deleteRecurringExpense(Connection conn, Scanner sc) throws SQLException {
        viewRecurringExpenses(conn);
        deleteItem(conn, sc, "Recurring Expense", "recurring_expenses", null);
    }

    private static void manageReminders(Connection conn, Scanner sc) throws SQLException {
        while (true) {
            System.out.println("\n--- Manage Reminders ---\n1. View All\n2. Add\n3. Delete\n4. Back");
            System.out.print("Choose option: ");
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1":
                    viewReminders(conn, false);
                    break;
                case "2":
                    addReminder(conn, sc);
                    break;
                case "3":
                    deleteReminder(conn, sc);
                    break;
                case "4":
                    return;
                default:
                    System.out.println("Invalid choice (1-4).");
                    break;
            }
            if (!choice.equals("4"))
                promptForEnter(sc);
        }
    }

    private static void viewReminders(Connection conn, boolean upcomingOnly) throws SQLException {
        String sql = "SELECT id, title, due_date, notes FROM reminders WHERE username = ?";
        if (upcomingOnly)
            sql += " AND due_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL ? DAY)";
        sql += " ORDER BY due_date";

        System.out.printf("\n--- %s Reminders ---\n",
                upcomingOnly ? "Upcoming (Next " + REMINDER_DAYS_AHEAD + " Days)" : "All");

        String[] headers = { "ID", "Title", "Due Date", "Notes" };
        List<String[]> rows = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentUser);
            if (upcomingOnly)
                ps.setInt(2, REMINDER_DAYS_AHEAD);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                rows.add(new String[] {
                        String.valueOf(rs.getInt("id")),
                        rs.getString("title"),
                        rs.getDate("due_date").toLocalDate().format(DATE_ONLY_FMT),
                        rs.getString("notes") == null ? "" : rs.getString("notes")
                });
            }
        }

        if (rows.isEmpty()) {
            System.out.printf("(No %sreminders found)\n", upcomingOnly ? "upcoming " : "");
        } else {
            TableUtils.printTable(headers, rows);
        }
    }

    private static void addReminder(Connection conn, Scanner sc) throws SQLException {
        System.out.println("\n--- Add New Reminder ---");
        String title = readNonEmptyString(sc, "Enter reminder title: ");
        LocalDate dueDate = readLocalDate(sc, "Enter due date (yyyy-MM-dd): ");
        System.out.print("Enter optional notes (press Enter to skip): ");
        String notes = sc.nextLine().trim();

        String sql = "INSERT INTO reminders (username, title, due_date, notes) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentUser);
            ps.setString(2, title);
            ps.setDate(3, java.sql.Date.valueOf(dueDate));
            ps.setString(4, notes.isEmpty() ? null : notes);
            if (ps.executeUpdate() > 0)
                System.out.println("Reminder added successfully!");
            else
                System.err.println("Failed to add reminder.");
        }
    }

    private static void deleteReminder(Connection conn, Scanner sc) throws SQLException {
        viewReminders(conn, false);
        deleteItem(conn, sc, "Reminder", "reminders", null);
    }

    private static void showPrediction(Connection conn) throws SQLException {
        System.out.println("\n📈 Predicted Expenses (based on last " + PREDICTION_MONTHS + " months average):");
        Map<String, BigDecimal> predictions = ExpenseService.getPredictions(conn, currentUser, PREDICTION_MONTHS);

        if (predictions.isEmpty()) {
            System.out.println("  (Not enough historical data for prediction)");
            return;
        }
        for (Map.Entry<String, BigDecimal> entry : predictions.entrySet()) {
            System.out.printf("  - %-20s: Rs. %.2f (Avg/month)\n", entry.getKey(), entry.getValue());
        }
        System.out.println("-".repeat(40));
    }

    private static void showDashboard(Connection conn) {
        if (currentUser == null)
            return;
        YearMonth currentMonth = YearMonth.now();
        System.out.printf("\n--- Dashboard: %s (%s) ---\n", currentUser, currentMonth.format(YM_FMT));
        System.out.println("-".repeat(40));
        try {
            BigDecimal totalThisMonth = ExpenseService.getTotalExpensesForMonth(conn, currentUser,
                    currentMonth.getMonthValue(), currentMonth.getYear());
            String topCategoryThisMonth = ExpenseService.getTopCategoryForMonth(conn, currentUser,
                    currentMonth.getMonthValue(), currentMonth.getYear());
            System.out.printf("  Total Expenses (This Month): Rs. %.2f\n", totalThisMonth);
            System.out.printf("  Top Spending (This Month):   %s\n", topCategoryThisMonth);

            if (monthlyBudget.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal remaining = monthlyBudget.subtract(totalThisMonth);
                System.out.printf("  Monthly Budget:              Rs. %.2f\n", monthlyBudget);
                System.out.printf("  Remaining Budget:            Rs. %.2f %s\n", remaining.abs(),
                        (remaining.compareTo(BigDecimal.ZERO) < 0 ? "⚠️ (Over Budget!)" : ""));
            } else
                System.out.println("  Monthly Budget:              Not Set");
            System.out.println("-".repeat(40));
            viewReminders(conn, true);
            System.out.println("-".repeat(40));
            showPrediction(conn);
        } catch (SQLException e) {
            System.err.println("Error fetching dashboard data: " + e.getMessage());
        }
        System.out.println("--- End Dashboard ---");
    }

    private static void printMainMenu() {
        String budgetStatus = (monthlyBudget.compareTo(BigDecimal.ZERO) > 0)
                ? String.format("Budget: Rs. %.2f", monthlyBudget)
                : "Budget: Not Set";
        System.out.printf("\n=== Main Menu === (User: %s | %s)\n", currentUser, budgetStatus);
        System.out.println(" 1. Add Expense        | 7. Manage Reminders");
        System.out.println(" 2. View Expenses      | 8. Search Expenses");
        System.out.println(" 3. Monthly Report     | 9. Show Dashboard");
        System.out.println(" 4. Delete Expense     | 10. Export to CSV");
        System.out.println(" 5. Set/View Budget    | 11. Manage Categories");
        System.out.println(" 6. Manage Recurring   | 12. Logout & Exit");
        System.out.println("================================================");
    }

    private static void promptForEnter(Scanner sc) {
        System.out.print("\nPress Enter to continue...");
        sc.nextLine();
    }

    private static int readInteger(Scanner sc, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String line = sc.nextLine();
            try {
                int val = Integer.parseInt(line);
                if (val >= min && val <= max)
                    return val;
                System.out.printf("Enter a number between %d and %d.\n", min, max);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number.");
            }
        }
    }

    private static int readUserOption(Scanner sc, int min, int max) {
        return readInteger(sc, "Choose option (" + min + "-" + max + "): ", min, max);
    }

    private static String readNonEmptyString(Scanner sc, String prompt) {
        while (true) {
            System.out.print(prompt);
            String val = sc.nextLine().trim();
            if (!val.isEmpty())
                return val;
            System.out.println("Input cannot be empty.");
        }
    }

    private static BigDecimal readPositiveBigDecimal(Scanner sc, String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = sc.nextLine();
            try {
                BigDecimal val = new BigDecimal(line);
                if (val.compareTo(BigDecimal.ZERO) > 0)
                    return val;
                System.out.println("Amount must be positive.");
            } catch (NumberFormatException e) {
                System.out.println("Invalid amount format.");
            }
        }
    }

    private static BigDecimal readNonNegativeBigDecimal(Scanner sc, String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = sc.nextLine();
            try {
                BigDecimal val = new BigDecimal(line);
                if (val.compareTo(BigDecimal.ZERO) >= 0)
                    return val;
                System.out.println("Amount cannot be negative.");
            } catch (NumberFormatException e) {
                System.out.println("Invalid amount format.");
            }
        }
    }

    private static LocalDate readLocalDate(Scanner sc, String prompt) {
        DateTimeFormatter inputFmt = DateTimeFormatter.ISO_LOCAL_DATE;
        while (true) {
            System.out.print(prompt);
            String line = sc.nextLine().trim();
            try {
                return LocalDate.parse(line, inputFmt);
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date format. Use yyyy-MM-dd.");
            }
        }
    }

    private static Month readMonth(Scanner sc, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = sc.nextLine().trim();
            try {
                int monthNum = Integer.parseInt(input);
                if (monthNum >= 1 && monthNum <= 12)
                    return Month.of(monthNum);
                else
                    System.out.println("Month number must be 1-12.");
            } catch (NumberFormatException e) {
                try {
                    for (Month m : Month.values()) {
                        if (m.name().equalsIgnoreCase(input) ||
                                m.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                                        .equalsIgnoreCase(input)) {
                            return m;
                        }
                    }
                    System.out.println("Invalid month name/abbr (e.g., 1, Jan, July).");
                } catch (Exception ex) {
                    System.out.println("Invalid month input.");
                }
            }
        }
    }

    private static void searchExpensesUI(Connection conn, Scanner sc) throws SQLException {
        System.out.println("\n--- Search Expenses ---");
        System.out.println("Enter filters (press Enter to skip):");
        System.out.print("Category keyword: ");
        String cat = sc.nextLine().trim();
        System.out.print("Min amount: ");
        BigDecimal min = readOptionalBigDecimal(sc);
        System.out.print("Max amount: ");
        BigDecimal max = readOptionalBigDecimal(sc);
        System.out.print("Start Date (yyyy-MM-dd): ");
        LocalDate start = readOptionalDate(sc);
        System.out.print("End Date (yyyy-MM-dd): ");
        LocalDate end = readOptionalDate(sc);

        List<Expense> results = ExpenseService.searchExpenses(conn, currentUser, null, cat, min, max, start, end);

        if (results.isEmpty()) {
            System.out.println("No expenses found matching the criteria.");
        } else {
            String[] headers = { "ID", "Category", "Amount", "Date" };
            List<String[]> rows = new ArrayList<>();
            for (Expense e : results) {
                rows.add(new String[] {
                        String.valueOf(e.getId()),
                        e.getCategory(),
                        String.format("Rs. %.2f", e.getAmount()),
                        e.getFormattedDate()
                });
            }
            TableUtils.printTable(headers, rows);
        }
    }

    private static BigDecimal readOptionalBigDecimal(Scanner sc) {
        String input = sc.nextLine().trim();
        if (input.isEmpty())
            return null;
        try {
            return new BigDecimal(input);
        } catch (Exception e) {
            System.out.println("Invalid amount, skipping filter.");
            return null;
        }
    }

    private static LocalDate readOptionalDate(Scanner sc) {
        String input = sc.nextLine().trim();
        if (input.isEmpty())
            return null;
        try {
            return LocalDate.parse(input);
        } catch (Exception e) {
            System.out.println("Invalid date format, skipping filter.");
            return null;
        }
    }

    private static void manageCategoriesUI(Connection conn, Scanner sc) throws SQLException {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Manage Categories ---");
            List<String> categories = ExpenseService.getCategories(conn);
            System.out.println("Current: " + String.join(", ", categories));
            System.out.println("1. Add Category");
            System.out.println("2. Delete Category");
            System.out.println("3. Back to Main Menu");
            int choice = readInteger(sc, "Choose: ", 1, 3);
            switch (choice) {
                case 1 -> {
                    String name = readNonEmptyString(sc, "Enter new category name: ");
                    if (ExpenseService.addCategory(conn, name))
                        System.out.println("Category added!");
                    else
                        System.out.println("Failed to add category (check if it already exists).");
                }
                case 2 -> {
                    String name = readNonEmptyString(sc, "Enter category name to delete: ");
                    if (ExpenseService.deleteCategory(conn, name))
                        System.out.println("Category deleted!");
                    else
                        System.out.println("Category not found.");
                }
                case 3 -> back = true;
            }
        }
    }
}
