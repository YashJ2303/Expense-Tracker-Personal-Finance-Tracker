package model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Expense {
    private final int id;
    private final String category;
    private final BigDecimal amount;
    private final String currency;
    private final String receiptPath;
    private final LocalDateTime date;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    public Expense(int id, String category, BigDecimal amount, LocalDateTime date) {
        this(id, category, amount, "INR", null, date);
    }

    public Expense(int id, String category, BigDecimal amount, String currency, String receiptPath,
            LocalDateTime date) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Category cannot be empty.");
        }
        this.id = id;
        this.category = category;
        this.amount = amount;
        this.currency = (currency != null) ? currency : "INR";
        this.receiptPath = receiptPath;
        this.date = (date != null) ? date : LocalDateTime.now();
    }

    public int getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReceiptPath() {
        return receiptPath;
    }

    public String getFormattedDate() {
        return date.format(FMT);
    }

    @Override
    public String toString() {
        return String.format("ID: %d, Category: %s, Amount: Rs. %.2f, Date: %s",
                id, category, amount, getFormattedDate());
    }
}
