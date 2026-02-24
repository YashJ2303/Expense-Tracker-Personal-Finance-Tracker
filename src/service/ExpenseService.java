package service;

import model.Expense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExpenseService {

    // ─── Existing Methods ────────────────────────────────

    public static BigDecimal getTotalExpensesForMonth(Connection conn, String currentUser, int month, int year)
            throws SQLException {
        String sql = "SELECT SUM(amount) FROM expenses WHERE username = ? AND MONTH(date) = ? AND YEAR(date) = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentUser);
            ps.setInt(2, month);
            ps.setInt(3, year);
            ResultSet rs = ps.executeQuery();
            return (rs.next() && rs.getBigDecimal(1) != null) ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    public static String getTopCategoryForMonth(Connection conn, String currentUser, int month, int year)
            throws SQLException {
        String sql = "SELECT category FROM expenses WHERE username = ? AND MONTH(date) = ? AND YEAR(date) = ? GROUP BY category ORDER BY SUM(amount) DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentUser);
            ps.setInt(2, month);
            ps.setInt(3, year);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : "N/A";
        }
    }

    public static Map<String, BigDecimal> getCategoryBreakdown(Connection conn, String currentUser, int month, int year)
            throws SQLException {
        String sql = "SELECT category, SUM(amount) AS total FROM expenses WHERE username = ? AND MONTH(date) = ? AND YEAR(date) = ? GROUP BY category ORDER BY total DESC";
        Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentUser);
            ps.setInt(2, month);
            ps.setInt(3, year);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                breakdown.put(rs.getString("category"), rs.getBigDecimal("total"));
            }
        }
        return breakdown;
    }

    public static Map<String, BigDecimal> getPredictions(Connection conn, String currentUser, int monthsToLookBack)
            throws SQLException {
        Map<String, BigDecimal> predictions = new LinkedHashMap<>();
        LocalDate startDate = LocalDate.now().minusMonths(monthsToLookBack).withDayOfMonth(1);
        String sql = """
                SELECT category, SUM(amount) AS total_sum, COUNT(DISTINCT YEAR(date), MONTH(date)) AS month_count
                FROM expenses WHERE username = ? AND date >= ? GROUP BY category ORDER BY total_sum DESC""";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentUser);
            ps.setDate(2, java.sql.Date.valueOf(startDate));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                BigDecimal totalSum = rs.getBigDecimal("total_sum");
                int monthCount = rs.getInt("month_count");
                if (monthCount > 0) {
                    predictions.put(rs.getString("category"),
                            totalSum.divide(BigDecimal.valueOf(monthCount), 2, RoundingMode.HALF_UP));
                }
            }
        }
        return predictions;
    }

    public static List<Expense> searchExpenses(Connection conn, String currentUser, String category, String keyword,
            BigDecimal minAmount, BigDecimal maxAmount, LocalDate startDate, LocalDate endDate) throws SQLException {
        List<Expense> results = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, category, amount, currency, receipt_path, date FROM expenses WHERE username = ?");
        List<Object> params = new ArrayList<>();
        params.add(currentUser);

        if (category != null && !category.trim().isEmpty()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND category LIKE ?");
            params.add("%" + keyword + "%");
        }
        if (minAmount != null) {
            sql.append(" AND amount >= ?");
            params.add(minAmount);
        }
        if (maxAmount != null) {
            sql.append(" AND amount <= ?");
            params.add(maxAmount);
        }
        if (startDate != null) {
            sql.append(" AND date >= ?");
            params.add(java.sql.Date.valueOf(startDate));
        }
        if (endDate != null) {
            sql.append(" AND date < ?");
            params.add(java.sql.Date.valueOf(endDate.plusDays(1)));
        }
        sql.append(" ORDER BY date DESC");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(new Expense(
                        rs.getInt("id"),
                        rs.getString("category"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("receipt_path"),
                        rs.getTimestamp("date").toLocalDateTime()));
            }
        }
        return results;
    }

    // ─── Recurring Expenses logic ──────────────────────────

    public static void applyRecurringExpenses(Connection conn, String username) throws SQLException {
        String sql = "SELECT * FROM recurring_expenses WHERE username = ? AND (last_applied_date IS NULL OR last_applied_date < CURRENT_DATE)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                LocalDate lastApplied = rs.getDate("last_applied_date") != null
                        ? rs.getDate("last_applied_date").toLocalDate()
                        : null;
                LocalDate startDate = rs.getDate("start_date").toLocalDate();
                String interval = rs.getString("interval_type");
                LocalDate nextDate = (lastApplied == null) ? startDate : calculateNextDate(lastApplied, interval);

                while (nextDate.isBefore(LocalDate.now()) || nextDate.isEqual(LocalDate.now())) {
                    // Add expense
                    addExpense(conn, username, rs.getString("category"), rs.getBigDecimal("amount"), "INR", null,
                            nextDate.atStartOfDay());
                    lastApplied = nextDate;
                    nextDate = calculateNextDate(lastApplied, interval);
                }

                // Update recurring record
                PreparedStatement up = conn
                        .prepareStatement("UPDATE recurring_expenses SET last_applied_date = ? WHERE id = ?");
                up.setDate(1, java.sql.Date.valueOf(lastApplied));
                up.setInt(2, rs.getInt("id"));
                up.executeUpdate();
            }
        }
    }

    private static LocalDate calculateNextDate(LocalDate last, String interval) {
        return switch (interval.toLowerCase()) {
            case "daily" -> last.plusDays(1);
            case "weekly" -> last.plusWeeks(1);
            case "monthly" -> last.plusMonths(1);
            default -> last.plusMonths(1);
        };
    }

    public static void addExpense(Connection conn, String user, String cat, BigDecimal amt, String curr, String receipt,
            java.time.LocalDateTime dt) throws SQLException {
        String sql = "INSERT INTO expenses (username, category, amount, currency, receipt_path, date) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user);
            ps.setString(2, cat);
            ps.setBigDecimal(3, amt);
            ps.setString(4, (curr != null) ? curr : "INR");
            ps.setString(5, receipt);
            ps.setTimestamp(6, java.sql.Timestamp.valueOf(dt != null ? dt : java.time.LocalDateTime.now()));
            ps.executeUpdate();
        }
    }

    public static List<String> getCategories(Connection conn) throws SQLException {
        List<String> categories = new ArrayList<>();
        String sql = "SELECT name FROM categories ORDER BY name";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                categories.add(rs.getString("name"));
            }
        }
        return categories;
    }

    public static boolean addCategory(Connection conn, String name) throws SQLException {
        String sql = "INSERT INTO categories (name) VALUES (?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean deleteCategory(Connection conn, String name) throws SQLException {
        String sql = "DELETE FROM categories WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            return ps.executeUpdate() > 0;
        }
    }

    // ─── Budget Methods ──────────────────────────────────

    public static Map<String, BigDecimal> getBudgets(Connection conn, String username) throws SQLException {
        Map<String, BigDecimal> budgets = new LinkedHashMap<>();
        String sql = "SELECT category, monthly_limit FROM budgets WHERE username = ? ORDER BY category";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                budgets.put(rs.getString("category"), rs.getBigDecimal("monthly_limit"));
            }
        }
        return budgets;
    }

    public static boolean setBudget(Connection conn, String username, String category, BigDecimal limit)
            throws SQLException {
        String sql = "INSERT INTO budgets (username, category, monthly_limit) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE monthly_limit = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, category);
            ps.setBigDecimal(3, limit);
            ps.setBigDecimal(4, limit);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean deleteBudget(Connection conn, String username, String category) throws SQLException {
        String sql = "DELETE FROM budgets WHERE username = ? AND category = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, category);
            return ps.executeUpdate() > 0;
        }
    }

    /** Returns [{category, spent, limit}] for the current month */
    public static List<Map<String, Object>> getBudgetStatus(Connection conn, String username) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        int month = LocalDate.now().getMonthValue();
        int year = LocalDate.now().getYear();

        Map<String, BigDecimal> budgets = getBudgets(conn, username);
        Map<String, BigDecimal> spending = getCategoryBreakdown(conn, username, month, year);

        for (var entry : budgets.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", entry.getKey());
            item.put("limit", entry.getValue());
            item.put("spent", spending.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            result.add(item);
        }
        return result;
    }

    // ─── Trend Data ──────────────────────────────────────

    /** Monthly totals for the last N months */
    public static List<Map<String, Object>> getMonthlyTrend(Connection conn, String username, int months)
            throws SQLException {
        List<Map<String, Object>> trend = new ArrayList<>();
        String sql = "SELECT YEAR(date) AS y, MONTH(date) AS m, SUM(amount) AS total "
                + "FROM expenses WHERE username = ? AND date >= ? "
                + "GROUP BY YEAR(date), MONTH(date) ORDER BY y, m";
        LocalDate start = LocalDate.now().minusMonths(months - 1).withDayOfMonth(1);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setDate(2, java.sql.Date.valueOf(start));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("year", rs.getInt("y"));
                point.put("month", rs.getInt("m"));
                point.put("total", rs.getBigDecimal("total"));
                trend.add(point);
            }
        }
        return trend;
    }

    /** Daily spending for a given month (for heatmap) */
    public static Map<Integer, BigDecimal> getDailySpending(Connection conn, String username, int month, int year)
            throws SQLException {
        Map<Integer, BigDecimal> daily = new LinkedHashMap<>();
        String sql = "SELECT DAY(date) AS d, SUM(amount) AS total "
                + "FROM expenses WHERE username = ? AND MONTH(date) = ? AND YEAR(date) = ? "
                + "GROUP BY DAY(date) ORDER BY d";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setInt(2, month);
            ps.setInt(3, year);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                daily.put(rs.getInt("d"), rs.getBigDecimal("total"));
            }
        }
        return daily;
    }

    /** Get expense count for a user in a given month */
    public static int getExpenseCount(Connection conn, String username, int month, int year) throws SQLException {
        String sql = "SELECT COUNT(*) FROM expenses WHERE username = ? AND MONTH(date) = ? AND YEAR(date) = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setInt(2, month);
            ps.setInt(3, year);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
