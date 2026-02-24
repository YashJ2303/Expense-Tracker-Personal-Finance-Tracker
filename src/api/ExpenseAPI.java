package api;

import model.Expense;
import service.DatabaseManager;
import service.ExpenseService;
import security.SecurityUtils;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ExpenseAPI {
    private static final int PORT = 8080;
    private static final Map<String, String> sessions = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        DatabaseManager.initializeDatabase();
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Auth
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/signup", new SignupHandler());
        // Core
        server.createContext("/api/expenses", new ExpensesHandler());
        server.createContext("/api/categories", new CategoriesHandler());
        server.createContext("/api/dashboard", new DashboardHandler());
        server.createContext("/api/report", new ReportHandler());
        // New endpoints
        server.createContext("/api/budgets", new BudgetsHandler());
        server.createContext("/api/budget-status", new BudgetStatusHandler());
        server.createContext("/api/trends", new TrendsHandler());
        server.createContext("/api/daily-spending", new DailySpendingHandler());
        server.createContext("/api/predictions", new PredictionsHandler());
        server.createContext("/api/export", new ExportHandler());
        server.createContext("/api/profile", new ProfileHandler());
        server.createContext("/api/recurring", new RecurringExpensesHandler());
        server.createContext("/api/reminders", new RemindersHandler());
        // Static files
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Expense Tracker API running at http://localhost:" + PORT);
    }

    // ─── Utility Methods ─────────────────────────────────

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{"))
            json = json.substring(1);
        if (json.endsWith("}"))
            json = json.substring(0, json.length() - 1);
        for (String pair : json.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("\"", "");
                String val = kv[1].trim().replaceAll("\"", "");
                map.put(key, val);
            }
        }
        return map;
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty())
            return map;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2)
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return map;
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private static void handleCors(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        ex.sendResponseHeaders(204, -1);
    }

    private static String getUser(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return sessions.get(auth.substring(7));
        }
        return null;
    }

    private static String esc(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    // ─── Auth Handlers ───────────────────────────────────

    static class LoginHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                handleCors(ex);
                return;
            }
            try {
                Map<String, String> body = parseJson(readBody(ex));
                String username = body.get("username");
                String password = body.get("password");
                try (Connection conn = DatabaseManager.getConnection()) {
                    PreparedStatement ps = conn.prepareStatement(
                            "SELECT password_hash, salt FROM users WHERE username = ?");
                    ps.setString(1, username);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        String hash = rs.getString("password_hash");
                        String salt = rs.getString("salt");
                        if (SecurityUtils.verifyPassword(password, salt, hash)) {
                            String token = UUID.randomUUID().toString();
                            sessions.put(token, username);
                            // Apply recurring expenses on login
                            ExpenseService.applyRecurringExpenses(conn, username);
                            sendJson(ex, 200, "{\"token\":\"" + token + "\",\"username\":\"" + esc(username) + "\"}");
                            return;
                        }
                    }
                }
                sendJson(ex, 401, "{\"error\":\"Invalid username or password\"}");
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    static class SignupHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                handleCors(ex);
                return;
            }
            try {
                Map<String, String> body = parseJson(readBody(ex));
                String username = body.get("username");
                String password = body.get("password");
                if (username == null || username.length() < 1 || password == null || password.length() < 3) {
                    sendJson(ex, 400, "{\"error\":\"Username required, password min 3 chars\"}");
                    return;
                }
                String salt = SecurityUtils.generateSalt();
                String hash = SecurityUtils.hashPassword(password, salt);
                try (Connection conn = DatabaseManager.getConnection()) {
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO users (username, password_hash, salt) VALUES (?, ?, ?)");
                    ps.setString(1, username);
                    ps.setString(2, hash);
                    ps.setString(3, salt);
                    ps.executeUpdate();
                    String token = UUID.randomUUID().toString();
                    sessions.put(token, username);
                    sendJson(ex, 201, "{\"token\":\"" + token + "\",\"username\":\"" + esc(username) + "\"}");
                }
            } catch (java.sql.SQLIntegrityConstraintViolationException e) {
                sendJson(ex, 409, "{\"error\":\"Username already exists\"}");
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    // ─── Core Handlers ───────────────────────────────────

    static class ExpensesHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                handleCors(ex);
                return;
            }
            String user = getUser(ex);
            if (user == null) {
                sendJson(ex, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            try (Connection conn = DatabaseManager.getConnection()) {
                switch (ex.getRequestMethod()) {
                    case "GET" -> {
                        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                        String category = q.get("category");
                        String keyword = q.get("keyword");
                        BigDecimal minAmt = q.containsKey("minAmount") ? new BigDecimal(q.get("minAmount")) : null;
                        BigDecimal maxAmt = q.containsKey("maxAmount") ? new BigDecimal(q.get("maxAmount")) : null;
                        LocalDate start = q.containsKey("startDate") ? LocalDate.parse(q.get("startDate")) : null;
                        LocalDate end = q.containsKey("endDate") ? LocalDate.parse(q.get("endDate")) : null;
                        List<Expense> expenses = ExpenseService.searchExpenses(conn, user, category, keyword, minAmt,
                                maxAmt, start, end);
                        StringBuilder sb = new StringBuilder("[");
                        for (int i = 0; i < expenses.size(); i++) {
                            Expense e = expenses.get(i);
                            if (i > 0)
                                sb.append(",");
                            String rp = e.getReceiptPath();
                            sb.append(String.format(
                                    "{\"id\":%d,\"category\":\"%s\",\"amount\":%.2f,\"currency\":\"%s\",\"receiptPath\":%s,\"date\":\"%s\"}",
                                    e.getId(), esc(e.getCategory()), e.getAmount(), esc(e.getCurrency()),
                                    (rp != null && !rp.isEmpty()) ? "\"" + esc(rp) + "\"" : "null",
                                    e.getFormattedDate()));
                        }
                        sb.append("]");
                        sendJson(ex, 200, sb.toString());
                    }
                    case "POST" -> {
                        Map<String, String> body = parseJson(readBody(ex));
                        String cat = body.get("category");
                        BigDecimal amt = new BigDecimal(body.get("amount"));
                        String curr = body.getOrDefault("currency", "INR");
                        String receipt = body.get("receiptPath");
                        ExpenseService.addExpense(conn, user, cat, amt, curr, receipt, LocalDateTime.now());
                        List<String> cats = ExpenseService.getCategories(conn);
                        if (!cats.contains(cat)) {
                            try {
                                ExpenseService.addCategory(conn, cat);
                            } catch (Exception ignored) {
                            }
                        }
                        sendJson(ex, 201, "{\"message\":\"Expense added\"}");
                    }
                    case "DELETE" -> {
                        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                        int id = Integer.parseInt(q.get("id"));
                        PreparedStatement ps = conn.prepareStatement(
                                "DELETE FROM expenses WHERE id = ? AND username = ?");
                        ps.setInt(1, id);
                        ps.setString(2, user);
                        int rows = ps.executeUpdate();
                        sendJson(ex, 200, "{\"deleted\":" + rows + "}");
                    }
                    default -> sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
                }
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    static class CategoriesHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                handleCors(ex);
                return;
            }
            String user = getUser(ex);
            if (user == null) {
                sendJson(ex, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            try (Connection conn = DatabaseManager.getConnection()) {
                switch (ex.getRequestMethod()) {
                    case "GET" -> {
                        List<String> cats = ExpenseService.getCategories(conn);
                        StringBuilder sb = new StringBuilder("[");
                        for (int i = 0; i < cats.size(); i++) {
                            if (i > 0)
                                sb.append(",");
                            sb.append("\"").append(esc(cats.get(i))).append("\"");
                        }
                        sb.append("]");
                        sendJson(ex, 200, sb.toString());
                    }
                    case "POST" -> {
                        Map<String, String> body = parseJson(readBody(ex));
                        String name = body.get("name");
                        if (ExpenseService.addCategory(conn, name))
                            sendJson(ex, 201, "{\"message\":\"Category added\"}");
                        else
                            sendJson(ex, 400, "{\"error\":\"Failed to add category\"}");
                    }
                    case "DELETE" -> {
                        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                        String name = q.get("name");
                        if (ExpenseService.deleteCategory(conn, name))
                            sendJson(ex, 200, "{\"message\":\"Category deleted\"}");
                        else
                            sendJson(ex, 400, "{\"error\":\"Category not found\"}");
                    }
                    default -> sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
                }
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    static class DashboardHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                handleCors(ex);
                return;
            }
            String user = getUser(ex);
            if (user == null) {
                sendJson(ex, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            try (Connection conn = DatabaseManager.getConnection()) {
                LocalDate now = LocalDate.now();
                int month = now.getMonthValue();
                int year = now.getYear();
                BigDecimal total = ExpenseService.getTotalExpensesForMonth(conn, user, month, year);
                String topCat = ExpenseService.getTopCategoryForMonth(conn, user, month, year);
                int count = ExpenseService.getExpenseCount(conn, user, month, year);

                // Recent 5 expenses
                List<Expense> recent = ExpenseService.searchExpenses(conn, user, null, null, null, null, null, null);
                StringBuilder recentJson = new StringBuilder("[");
                int limit = Math.min(recent.size(), 5);
                for (int i = 0; i < limit; i++) {
                    Expense e = recent.get(i);
                    if (i > 0)
                        recentJson.append(",");
                    recentJson.append(String.format(
                            "{\"category\":\"%s\",\"amount\":%.2f,\"date\":\"%s\"}",
                            esc(e.getCategory()), e.getAmount(), e.getFormattedDate()));
                }
                recentJson.append("]");

                // Budget alerts for dashboard
                List<Map<String, Object>> budgetStatus = ExpenseService.getBudgetStatus(conn, user);
                StringBuilder alertsJson = new StringBuilder("[");
                int alertIdx = 0;
                for (var bs : budgetStatus) {
                    BigDecimal spent = (BigDecimal) bs.get("spent");
                    BigDecimal budgetLimit = (BigDecimal) bs.get("limit");
                    double pct = budgetLimit.doubleValue() > 0 ? (spent.doubleValue() / budgetLimit.doubleValue() * 100)
                            : 0;
                    if (pct >= 80) { // Show alert at 80%+
                        if (alertIdx > 0)
                            alertsJson.append(",");
                        alertsJson.append(String.format(
                                "{\"category\":\"%s\",\"spent\":%.2f,\"limit\":%.2f,\"percent\":%.1f}",
                                esc((String) bs.get("category")), spent, budgetLimit, pct));
                        alertIdx++;
                    }
                }
                alertsJson.append("]");

                String[] monthNames = { "", "January", "February", "March", "April", "May", "June",
                        "July", "August", "September", "October", "November", "December" };

                String json = String.format(
                        "{\"monthlyTotal\":%.2f,\"topCategory\":\"%s\",\"expenseCount\":%d,\"month\":\"%s %d\",\"recent\":%s,\"budgetAlerts\":%s}",
                        total, esc(topCat), count, monthNames[month], year, recentJson, alertsJson);
                sendJson(ex, 200, json);
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    static class ReportHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                handleCors(ex);
                return;
            }
            String user = getUser(ex);
            if (user == null) {
                sendJson(ex, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            try (Connection conn = DatabaseManager.getConnection()) {
                Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                int month = Integer.parseInt(q.getOrDefault("month", String.valueOf(LocalDate.now().getMonthValue())));
                int year = Integer.parseInt(q.getOrDefault("year", String.valueOf(LocalDate.now().getYear())));
                Map<String, BigDecimal> breakdown = ExpenseService.getCategoryBreakdown(conn, user, month, year);
                BigDecimal total = ExpenseService.getTotalExpensesForMonth(conn, user, month, year);
                StringBuilder sb = new StringBuilder("{\"total\":" + total + ",\"breakdown\":[");
                int i = 0;
                for (var entry : breakdown.entrySet()) {
                    if (i > 0)
                        sb.append(",");
                    sb.append(String.format("{\"category\":\"%s\",\"amount\":%.2f}", esc(entry.getKey()),
                            entry.getValue()));
                    i++;
                }
                sb.append("]}");
                sendJson(ex, 200, sb.toString());
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    // ─── New Feature Handlers ────────────────────────────

    static class BudgetsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                handleCors(ex);
                return;
            }
            String user = getUser(ex);
            if (user == null) {
                sendJson(ex, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            try (Connection conn = DatabaseManager.getConnection()) {
                switch (ex.getRequestMethod()) {
                    case "GET" -> {
                        List<Map<String, Object>> status = ExpenseService.getBudgetStatus(conn, user);
                        StringBuilder sb = new StringBuilder("[");
                        for (int i = 0; i < status.size(); i++) {
                            if (i > 0)
                                sb.append(",");
                            var item = status.get(i);
                            sb.append(String.format("{\"category\":\"%s\",\"spent\":%.2f,\"limit\":%.2f}",
                                    esc((String) item.get("category")), item.get("spent"), item.get("limit")));
                        }
                        sb.append("]");
                        sendJson(ex, 200, sb.toString());
                    }
                    case "POST" -> {
                        Map<String, String> body = parseJson(readBody(ex));
                        String cat = body.get("category");
                        BigDecimal limit = new BigDecimal(body.get("limit"));
                        if (ExpenseService.setBudget(conn, user, cat, limit))
                            sendJson(ex, 201, "{\"message\":\"Budget set\"}");
                        else
                            sendJson(ex, 400, "{\"error\":\"Failed to set budget\"}");
                    }
                    case "DELETE" -> {
                        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                        String cat = q.get("category");
                        if (ExpenseService.deleteBudget(conn, user, cat))
                            sendJson(ex, 200, "{\"message\":\"Budget removed\"}");
                        else
                            sendJson(ex, 400, "{\"error\":\"Budget not found\"}");
                    }
                    default -> sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
                }
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    static class BudgetStatusHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                handleCors(ex);
                return;
            }
            String user = getUser(ex);
            if (user == null) {
                sendJson(ex, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            try (Connection conn = DatabaseManager.getConnection()) {
                List<Map<String, Object>> status = ExpenseService.getBudgetStatus(conn, user);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < status.size(); i++) {
                    if (i > 0)
                        sb.append(",");
                    var item = status.get(i);
                    sb.append(String.format("{\"category\":\"%s\",\"spent\":%.2f,\"limit\":%.2f}",
                            esc((String) item.get("category")), item.get("spent"), item.get("limit")));
                }
                sb.append("]");
                sendJson(ex, 200, sb.toString());
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    static class TrendsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                handleCors(ex);
                return;
            }
            String user = getUser(ex);
            if (user == null) {
                sendJson(ex, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            try (Connection conn = DatabaseManager.getConnection()) {
                Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                int months = Integer.parseInt(q.getOrDefault("months", "6"));
                List<Map<String, Object>> trend = ExpenseService.getMonthlyTrend(conn, user, months);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < trend.size(); i++) {
                    if (i > 0)
                        sb.append(",");
                    var pt = trend.get(i);
                    sb.append(String.format("{\"year\":%d,\"month\":%d,\"total\":%.2f}",
                            pt.get("year"), pt.get("month"), pt.get("total")));
                }
                sb.append("]");
                sendJson(ex, 200, sb.toString());
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    static class DailySpendingHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                handleCors(ex);
                return;
            }
            String user = getUser(ex);
            if (user == null) {
                sendJson(ex, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            try (Connection conn = DatabaseManager.getConnection()) {
                Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                int month = Integer.parseInt(q.getOrDefault("month", String.valueOf(LocalDate.now().getMonthValue())));
                int year = Integer.parseInt(q.getOrDefault("year", String.valueOf(LocalDate.now().getYear())));
                Map<Integer, BigDecimal> daily = ExpenseService.getDailySpending(conn, user, month, year);
                StringBuilder sb = new StringBuilder("{");
                int i = 0;
                for (var entry : daily.entrySet()) {
                    if (i > 0)
                        sb.append(",");
                    sb.append(String.format("\"%d\":%.2f", entry.getKey(), entry.getValue()));
                    i++;
                }
                sb.append("}");
                sendJson(ex, 200, sb.toString());
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    static class PredictionsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                handleCors(ex);
                return;
            }
            String user = getUser(ex);
            if (user == null) {
                sendJson(ex, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            try (Connection conn = DatabaseManager.getConnection()) {
                Map<String, BigDecimal> predictions = ExpenseService.getPredictions(conn, user, 3);
                BigDecimal totalPredicted = BigDecimal.ZERO;
                StringBuilder cats = new StringBuilder("[");
                int i = 0;
                for (var entry : predictions.entrySet()) {
                    if (i > 0)
                        cats.append(",");
                    cats.append(String.format("{\"category\":\"%s\",\"predicted\":%.2f}", esc(entry.getKey()),
                            entry.getValue()));
                    totalPredicted = totalPredicted.add(entry.getValue());
                    i++;
                }
                cats.append("]");
                String json = String.format("{\"totalPredicted\":%.2f,\"categories\":%s}", totalPredicted, cats);
                sendJson(ex, 200, json);
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    static class ExportHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                handleCors(ex);
                return;
            }
            String user = getUser(ex);
            if (user == null) {
                sendJson(ex, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            try (Connection conn = DatabaseManager.getConnection()) {
                List<Expense> expenses = ExpenseService.searchExpenses(conn, user, null, null, null, null, null, null);
                StringBuilder csv = new StringBuilder("ID,Category,Amount,Date\n");
                for (Expense e : expenses) {
                    csv.append(String.format("%d,\"%s\",%.2f,%s\n",
                            e.getId(), e.getCategory().replace("\"", "\"\""), e.getAmount(), e.getFormattedDate()));
                }
                byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
                ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=expenses.csv");
                ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
                ex.sendResponseHeaders(200, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.getResponseBody().close();
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    static class RecurringExpensesHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                handleCors(ex);
                return;
            }
            String user = getUser(ex);
            if (user == null) {
                sendJson(ex, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            try (Connection conn = DatabaseManager.getConnection()) {
                switch (ex.getRequestMethod()) {
                    case "GET" -> {
                        String sql = "SELECT * FROM recurring_expenses WHERE username = ?";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setString(1, user);
                            ResultSet rs = ps.executeQuery();
                            StringBuilder sb = new StringBuilder("[");
                            int i = 0;
                            while (rs.next()) {
                                if (i > 0)
                                    sb.append(",");
                                sb.append(String.format(
                                        "{\"id\":%d,\"description\":\"%s\",\"amount\":%.2f,\"category\":\"%s\",\"interval\":\"%s\",\"startDate\":\"%s\"}",
                                        rs.getInt("id"), esc(rs.getString("description")), rs.getBigDecimal("amount"),
                                        esc(rs.getString("category")), rs.getString("interval_type"),
                                        rs.getDate("start_date").toString()));
                                i++;
                            }
                            sb.append("]");
                            sendJson(ex, 200, sb.toString());
                        }
                    }
                    case "POST" -> {
                        Map<String, String> body = parseJson(readBody(ex));
                        String desc = body.get("description");
                        BigDecimal amt = new BigDecimal(body.get("amount"));
                        String cat = body.get("category");
                        String interval = body.get("interval");
                        String start = body.get("startDate");

                        String sql = "INSERT INTO recurring_expenses (username, description, amount, category, interval_type, start_date) VALUES (?, ?, ?, ?, ?, ?)";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setString(1, user);
                            ps.setString(2, desc);
                            ps.setBigDecimal(3, amt);
                            ps.setString(4, cat);
                            ps.setString(5, interval);
                            ps.setDate(6, java.sql.Date.valueOf(start != null ? start : LocalDate.now().toString()));
                            ps.executeUpdate();
                        }
                        sendJson(ex, 201, "{\"message\":\"Recurring expense added\"}");
                    }
                    case "DELETE" -> {
                        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                        int id = Integer.parseInt(q.get("id"));
                        String sql = "DELETE FROM recurring_expenses WHERE id = ? AND username = ?";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setInt(1, id);
                            ps.setString(2, user);
                            int rows = ps.executeUpdate();
                            sendJson(ex, 200, "{\"deleted\":" + rows + "}");
                        }
                    }
                    default -> sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
                }
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    static class ProfileHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                handleCors(ex);
                return;
            }
            String user = getUser(ex);
            if (user == null) {
                sendJson(ex, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            if (!"POST".equals(ex.getRequestMethod())) {
                sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            try (Connection conn = DatabaseManager.getConnection()) {
                Map<String, String> body = parseJson(readBody(ex));
                String currentPass = body.get("currentPassword");
                String newPass = body.get("newPassword");
                if (newPass == null || newPass.length() < 3) {
                    sendJson(ex, 400, "{\"error\":\"New password must be at least 3 characters\"}");
                    return;
                }
                // Verify current password
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT password_hash, salt FROM users WHERE username = ?");
                ps.setString(1, user);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String hash = rs.getString("password_hash");
                    String salt = rs.getString("salt");
                    if (!SecurityUtils.verifyPassword(currentPass, salt, hash)) {
                        sendJson(ex, 401, "{\"error\":\"Current password is incorrect\"}");
                        return;
                    }
                    // Update password
                    String newSalt = SecurityUtils.generateSalt();
                    String newHash = SecurityUtils.hashPassword(newPass, newSalt);
                    PreparedStatement up = conn.prepareStatement(
                            "UPDATE users SET password_hash = ?, salt = ? WHERE username = ?");
                    up.setString(1, newHash);
                    up.setString(2, newSalt);
                    up.setString(3, user);
                    up.executeUpdate();
                    sendJson(ex, 200, "{\"message\":\"Password updated successfully\"}");
                } else {
                    sendJson(ex, 404, "{\"error\":\"User not found\"}");
                }
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    // ─── Reminders Handler ──────────────────────────────

    static class RemindersHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                handleCors(ex);
                return;
            }
            String user = getUser(ex);
            if (user == null) {
                sendJson(ex, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            try (Connection conn = DatabaseManager.getConnection()) {
                switch (ex.getRequestMethod()) {
                    case "GET" -> {
                        String sql = "SELECT id, title, due_date, notes FROM reminders WHERE username = ? ORDER BY due_date ASC";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setString(1, user);
                            ResultSet rs = ps.executeQuery();
                            StringBuilder sb = new StringBuilder("[");
                            int i = 0;
                            while (rs.next()) {
                                if (i > 0)
                                    sb.append(",");
                                String notes = rs.getString("notes");
                                sb.append(String.format(
                                        "{\"id\":%d,\"title\":\"%s\",\"dueDate\":\"%s\",\"notes\":%s}",
                                        rs.getInt("id"), esc(rs.getString("title")),
                                        rs.getDate("due_date").toString(),
                                        notes != null ? "\"" + esc(notes) + "\"" : "null"));
                                i++;
                            }
                            sb.append("]");
                            sendJson(ex, 200, sb.toString());
                        }
                    }
                    case "POST" -> {
                        Map<String, String> body = parseJson(readBody(ex));
                        String title = body.get("title");
                        String dueDate = body.get("dueDate");
                        String notes = body.get("notes");
                        if (title == null || title.isEmpty() || dueDate == null || dueDate.isEmpty()) {
                            sendJson(ex, 400, "{\"error\":\"Title and due date are required\"}");
                            return;
                        }
                        String sql = "INSERT INTO reminders (username, title, due_date, notes) VALUES (?, ?, ?, ?)";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setString(1, user);
                            ps.setString(2, title);
                            ps.setDate(3, java.sql.Date.valueOf(dueDate));
                            ps.setString(4, notes);
                            ps.executeUpdate();
                        }
                        sendJson(ex, 201, "{\"message\":\"Reminder added\"}");
                    }
                    case "DELETE" -> {
                        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
                        int id = Integer.parseInt(q.get("id"));
                        String sql = "DELETE FROM reminders WHERE id = ? AND username = ?";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setInt(1, id);
                            ps.setString(2, user);
                            int rows = ps.executeUpdate();
                            sendJson(ex, 200, "{\"deleted\":" + rows + "}");
                        }
                    }
                    default -> sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
                }
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        }
    }

    // ─── Static File Server ──────────────────────────────

    static class StaticFileHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/"))
                path = "/index.html";

            Path filePath = Paths.get("web" + path).normalize();
            if (!filePath.startsWith("web")) {
                ex.sendResponseHeaders(403, -1);
                return;
            }
            if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                String mime = "text/plain";
                String name = filePath.toString().toLowerCase();
                if (name.endsWith(".html"))
                    mime = "text/html; charset=utf-8";
                else if (name.endsWith(".css"))
                    mime = "text/css; charset=utf-8";
                else if (name.endsWith(".js"))
                    mime = "application/javascript; charset=utf-8";
                else if (name.endsWith(".json"))
                    mime = "application/json; charset=utf-8";
                else if (name.endsWith(".png"))
                    mime = "image/png";
                else if (name.endsWith(".jpg") || name.endsWith(".jpeg"))
                    mime = "image/jpeg";
                else if (name.endsWith(".svg"))
                    mime = "image/svg+xml";
                else if (name.endsWith(".ico"))
                    mime = "image/x-icon";

                byte[] bytes = Files.readAllBytes(filePath);
                ex.getResponseHeaders().set("Content-Type", mime);
                ex.sendResponseHeaders(200, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.getResponseBody().close();
            } else {
                String msg = "404 Not Found";
                ex.sendResponseHeaders(404, msg.length());
                ex.getResponseBody().write(msg.getBytes());
                ex.getResponseBody().close();
            }
        }
    }
}
