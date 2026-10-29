import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.Vector;

interface Trackable {
    void displayDetails();
}

abstract class Transaction {
    protected int id;
    protected double amount;
    protected String category;
    protected String description;
    protected LocalDate date;

    Transaction(int id, double amount, String category, String description, LocalDate date) {
        this.id = id;
        this.amount = amount;
        this.category = category;
        this.description = description;
        this.date = date;
    }

    abstract void showType();

    public int getId() {
        return id;
    }

    public double getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }
}

class Expense extends Transaction implements Trackable {
    private String paymentMode;

    Expense(int id, double amount, String category, String description, LocalDate date, String paymentMode) {
        super(id, amount, category, description, date);
        this.paymentMode = paymentMode;
    }

    @Override
    void showType() {
        System.out.println("Transaction Type : EXPENSE");
    }

    @Override
    public void displayDetails() {
        System.out.println("ID: " + id + " | Amount: Rs." + amount + " | Category: " + category + " | Description: " + description + " | Date: " + date + " | Payment Mode: " + paymentMode);
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = paymentMode;
    }
}

class Income extends Transaction implements Trackable {
    private String source;

    Income(int id, double amount, String category, String description, LocalDate date, String source) {
        super(id, amount, category, description, date);
        this.source = source;
    }

    @Override
    void showType() {
        System.out.println("Transaction Type : INCOME");
    }

    @Override
    public void displayDetails() {
        System.out.println("ID: " + id + " | Amount: Rs." + amount + " | Category: " + category + " | Description: " + description + " | Date: " + date + " | Source: " + source);
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}

class Budget {
    private String category;
    private double limit;

    Budget(String category, double limit) {
        this.category = category;
        this.limit = limit;
    }

    Budget(String category) {
        this.category = category;
        this.limit = 0;
    }

    public String getCategory() {
        return category;
    }

    public double getLimit() {
        return limit;
    }

    public void setLimit(double limit) {
        this.limit = limit;
    }

    public void displayBudget() {
        System.out.println("Category: " + category + " | Budget Limit: Rs." + limit);
    }
}

class ActivityEntry {
    int id;
    String message;
    String createdAt;

    ActivityEntry(int id, String message, String createdAt) {
        this.id = id;
        this.message = message;
        this.createdAt = createdAt;
    }
}

public class SmartExpenseBudgetTracker {
    static final String APP_NAME = "SMART EXPENSE & BUDGET TRACKER";
    static int transactionCount = 0;
    static String[] categories = { "Food", "Travel", "Shopping", "Education", "Entertainment", "Miscellaneous", "NA" };
    static ArrayList<Transaction> transactions = new ArrayList<>();
    static Vector<ActivityEntry> activityHistory = new Vector<>();
    static Budget[] budgets = new Budget[categories.length];
    static Scanner sc = new Scanner(System.in);
    static final String databaseUrl = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/smart_expense_budget_tracker");
    static final String databaseUser = System.getenv().getOrDefault("DB_USER", "postgres");

    public static void main(String[] args) throws Exception {
        initializeDatabase();
        loadPersistentState();
        startWebServer();
    }

    static Connection openDatabase() throws SQLException {
        String password = System.getenv("DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new SQLException("Set the DB_PASSWORD environment variable to your PostgreSQL password.");
        }
        return DriverManager.getConnection(databaseUrl, databaseUser, password);
    }

    static void initializeDatabase() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC driver is missing. Include postgresql-42.7.14.jar in the classpath.", e);
        }
        try (Connection connection = openDatabase(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS transactions (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, type VARCHAR(10) NOT NULL CHECK(type IN ('Income','Expense')), amount NUMERIC NOT NULL CHECK(amount > 0), category VARCHAR(40) NOT NULL, description TEXT NOT NULL, transaction_date DATE NOT NULL, income_source TEXT, payment_mode TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT transactions_type_details_check CHECK ((type = 'Income' AND income_source IS NOT NULL AND payment_mode IS NULL) OR (type = 'Expense' AND payment_mode IS NOT NULL AND income_source IS NULL)))");
            statement.execute("ALTER TABLE transactions ADD COLUMN IF NOT EXISTS income_source TEXT");
            statement.execute("ALTER TABLE transactions ADD COLUMN IF NOT EXISTS payment_mode TEXT");
            statement.execute("ALTER TABLE transactions ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
            statement.execute("ALTER TABLE transactions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
            boolean hasLegacyDetail;
            try (ResultSet column = statement.executeQuery("SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'transactions' AND column_name = 'detail')")) {
                hasLegacyDetail = column.next() && column.getBoolean(1);
            }
            if (hasLegacyDetail) {
                statement.executeUpdate("UPDATE transactions SET income_source = detail WHERE type = 'Income' AND income_source IS NULL");
                statement.executeUpdate("UPDATE transactions SET payment_mode = detail WHERE type = 'Expense' AND payment_mode IS NULL");
                statement.execute("ALTER TABLE transactions DROP COLUMN detail");
            }
            boolean hasTypeDetailsConstraint;
            try (ResultSet constraint = statement.executeQuery("SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.transactions'::regclass AND conname = 'transactions_type_details_check')")) {
                hasTypeDetailsConstraint = constraint.next() && constraint.getBoolean(1);
            }
            if (!hasTypeDetailsConstraint) {
                statement.execute("ALTER TABLE transactions ADD CONSTRAINT transactions_type_details_check CHECK ((type = 'Income' AND income_source IS NOT NULL AND payment_mode IS NULL) OR (type = 'Expense' AND payment_mode IS NOT NULL AND income_source IS NULL))");
            }
            statement.execute("CREATE TABLE IF NOT EXISTS budgets (category VARCHAR(40) PRIMARY KEY, amount_limit NUMERIC NOT NULL CHECK(amount_limit >= 0))");
            statement.execute("CREATE TABLE IF NOT EXISTS activity (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, message TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.execute("ALTER TABLE activity ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO budgets(category, amount_limit) VALUES (?, 0) ON CONFLICT (category) DO NOTHING")) {
                for (String category : categories) {
                    insert.setString(1, category);
                    insert.executeUpdate();
                }
            }
        }
    }

    static synchronized void loadPersistentState() throws SQLException {
        ArrayList<Transaction> savedTransactions = new ArrayList<>();
        Vector<ActivityEntry> savedActivity = new Vector<>();
        Budget[] savedBudgets = new Budget[categories.length];
        try (Connection connection = openDatabase(); Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("SELECT id, type, amount, category, description, transaction_date, income_source, payment_mode FROM transactions ORDER BY id")) {
                while (rows.next()) {
                    savedTransactions.add(transactionFromRow(rows));
                }
            }
            try (ResultSet rows = statement.executeQuery("SELECT category, amount_limit FROM budgets")) {
                while (rows.next()) {
                    for (int i = 0; i < categories.length; i++) {
                        if (categories[i].equals(rows.getString("category"))) {
                            savedBudgets[i] = new Budget(categories[i], rows.getDouble("amount_limit"));
                        }
                    }
                }
            }
            try (ResultSet rows = statement.executeQuery("SELECT id, message, created_at FROM activity ORDER BY id")) {
                while (rows.next()) {
                    savedActivity.add(new ActivityEntry(rows.getInt("id"), rows.getString("message"), rows.getTimestamp("created_at").toInstant().toString()));
                }
            }
            try (ResultSet rows = statement.executeQuery("SELECT COALESCE(MAX(id), 0) FROM transactions")) {
                transactionCount = rows.next() ? rows.getInt(1) : 0;
            }
        }
        for (int i = 0; i < savedBudgets.length; i++) {
            if (savedBudgets[i] == null) {
                savedBudgets[i] = new Budget(categories[i], 0);
            }
        }
        transactions = savedTransactions;
        activityHistory = savedActivity;
        budgets = savedBudgets;
    }

    static Transaction transactionFromRow(ResultSet row) throws SQLException {
        int id = row.getInt("id");
        String type = row.getString("type");
        double amount = row.getDouble("amount");
        String category = row.getString("category");
        String description = row.getString("description");
        LocalDate date = LocalDate.parse(row.getString("transaction_date"));
        if (type.equals("Income")) {
            return new Income(id, amount, category, description, date, row.getString("income_source"));
        }
        return new Expense(id, amount, category, description, date, row.getString("payment_mode"));
    }

    static void saveActivity(Connection connection, String message) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO activity(message) VALUES (?)")) {
            insert.setString(1, message);
            insert.executeUpdate();
        }
    }

    static void initializeBudgets() {
        for (int i = 0; i < budgets.length; i++) {
            budgets[i] = new Budget(categories[i], 0);
        }
    }

    static void startWebServer() throws IOException {
        ServerSocket server = new ServerSocket();
        server.bind(new InetSocketAddress("127.0.0.1", 8000));
        System.out.println(APP_NAME + " is running at http://localhost:8000/");
        System.out.println("Connected to PostgreSQL at " + databaseUrl + ".");
        while (true) {
            Socket client = server.accept();
            Thread thread = new Thread(() -> handleClient(client));
            thread.start();
        }
    }

    static void handleClient(Socket client) {
        try (Socket socket = client) {
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            String requestLine = readLine(input);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendResponse(socket.getOutputStream(), textResponse(400, "Bad request", "text/plain"));
                return;
            }
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(input)) != null && !line.isEmpty()) {
                int separator = line.indexOf(":");
                if (separator > -1) {
                    headers.put(line.substring(0, separator).trim().toLowerCase(), line.substring(separator + 1).trim());
                }
            }
            int length = 0;
            if (headers.containsKey("content-length")) {
                length = Integer.parseInt(headers.get("content-length"));
            }
            byte[] bodyBytes = input.readNBytes(length);
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            WebResponse response = route(parts[0], parts[1], body);
            sendResponse(socket.getOutputStream(), response);
        } catch (Exception e) {
            try {
                sendResponse(client.getOutputStream(), jsonResponse(500, errorJson("Server error.")));
            } catch (IOException ignored) {
            }
        }
    }

    static WebResponse route(String method, String target, String body) {
        try {
            URI uri = URI.create(target);
            String path = uri.getPath();
            Map<String, String> query = parseQuery(uri.getRawQuery());
            if (method.equalsIgnoreCase("OPTIONS")) {
                return textResponse(204, "", "text/plain");
            }
            if (path.equals("/api/state")) {
                if (!method.equalsIgnoreCase("GET")) {
                    return jsonResponse(405, errorJson("Method not allowed."));
                }
                loadPersistentState();
                return jsonResponse(200, buildStateJson());
            }
            if (path.equals("/api/transaction")) {
                if (!method.equalsIgnoreCase("POST")) {
                    return jsonResponse(405, errorJson("Method not allowed."));
                }
                return addTransactionResponse(parseQuery(body));
            }
            if (path.equals("/api/transaction/update")) {
                if (!method.equalsIgnoreCase("POST")) {
                    return jsonResponse(405, errorJson("Method not allowed."));
                }
                return updateTransactionResponse(parseQuery(body));
            }
            if (path.equals("/api/transaction/delete")) {
                if (!method.equalsIgnoreCase("POST")) {
                    return jsonResponse(405, errorJson("Method not allowed."));
                }
                return deleteTransactionResponse(parseQuery(body));
            }
            if (path.equals("/api/activity/delete")) {
                if (!method.equalsIgnoreCase("POST")) {
                    return jsonResponse(405, errorJson("Method not allowed."));
                }
                return deleteActivityResponse(parseQuery(body));
            }
            if (path.equals("/api/budget")) {
                if (!method.equalsIgnoreCase("POST")) {
                    return jsonResponse(405, errorJson("Method not allowed."));
                }
                return setBudgetResponse(parseQuery(body));
            }
            if (path.equals("/api/search")) {
                if (!method.equalsIgnoreCase("GET")) {
                    return jsonResponse(405, errorJson("Method not allowed."));
                }
                return searchResponse(query);
            }
            if (path.equals("/api/monthly")) {
                if (!method.equalsIgnoreCase("GET")) {
                    return jsonResponse(405, errorJson("Method not allowed."));
                }
                int year = Integer.parseInt(required(query, "year"));
                int month = Integer.parseInt(required(query, "month"));
                if (month < 1 || month > 12 || year < 2000 || year > 2100) {
                    throw new IllegalArgumentException("Invalid month or year.");
                }
                loadPersistentState();
                return jsonResponse(200, monthlyJson(year, month));
            }
            if (!method.equalsIgnoreCase("GET")) {
                return jsonResponse(405, errorJson("Method not allowed."));
            }
            return staticFileResponse(path);
        } catch (IllegalArgumentException e) {
            return jsonResponse(400, errorJson(e.getMessage()));
        } catch (SQLException e) {
            e.printStackTrace();
            return jsonResponse(500, errorJson("Database error. Your data was not changed."));
        } catch (Exception e) {
            e.printStackTrace();
            return jsonResponse(500, errorJson("Server error."));
        }
    }

    static synchronized WebResponse addTransactionResponse(Map<String, String> data) throws SQLException {
        String type = required(data, "type");
        if (!type.equalsIgnoreCase("Income") && !type.equalsIgnoreCase("Expense")) {
            throw new IllegalArgumentException("Invalid transaction type.");
        }
        type = type.equalsIgnoreCase("Income") ? "Income" : "Expense";
        double amount = positiveAmount(required(data, "amount"));
        String category = validCategory(required(data, "category"));
        String description = validText(required(data, "description"));
        LocalDate date = validDate(required(data, "date"));
        String detail = validText(required(data, "detail"));
        try (Connection connection = openDatabase()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO transactions(type, amount, category, description, transaction_date, income_source, payment_mode) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, type);
                insert.setDouble(2, amount);
                insert.setString(3, category);
                insert.setString(4, description);
                insert.setDate(5, java.sql.Date.valueOf(date));
                insert.setString(6, type.equals("Income") ? detail : null);
                insert.setString(7, type.equals("Expense") ? detail : null);
                insert.executeUpdate();
            }
            saveActivity(connection, type + " added: Rs." + amount + " - " + category);
            connection.commit();
        }
        loadPersistentState();
        return jsonResponse(200, "{\"success\":true}");
    }

    static synchronized WebResponse updateTransactionResponse(Map<String, String> data) throws SQLException {
        int id = positiveId(required(data, "id"));
        String type = required(data, "type");
        if (!type.equalsIgnoreCase("Income") && !type.equalsIgnoreCase("Expense")) {
            throw new IllegalArgumentException("Invalid transaction type.");
        }
        type = type.equalsIgnoreCase("Income") ? "Income" : "Expense";
        double amount = positiveAmount(required(data, "amount"));
        String category = validCategory(required(data, "category"));
        String description = validText(required(data, "description"));
        LocalDate date = validDate(required(data, "date"));
        String detail = validText(required(data, "detail"));
        try (Connection connection = openDatabase()) {
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement("UPDATE transactions SET type=?, amount=?, category=?, description=?, transaction_date=?, income_source=?, payment_mode=?, updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
                update.setString(1, type);
                update.setDouble(2, amount);
                update.setString(3, category);
                update.setString(4, description);
                update.setDate(5, java.sql.Date.valueOf(date));
                update.setString(6, type.equals("Income") ? detail : null);
                update.setString(7, type.equals("Expense") ? detail : null);
                update.setInt(8, id);
                if (update.executeUpdate() == 0) {
                    throw new IllegalArgumentException("Transaction not found.");
                }
            }
            saveActivity(connection, "Transaction updated: ID " + id);
            connection.commit();
        }
        loadPersistentState();
        return jsonResponse(200, "{\"success\":true}");
    }

    static synchronized WebResponse deleteTransactionResponse(Map<String, String> data) throws SQLException {
        int id = positiveId(required(data, "id"));
        try (Connection connection = openDatabase()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM transactions WHERE id=?")) {
                delete.setInt(1, id);
                if (delete.executeUpdate() == 0) {
                    throw new IllegalArgumentException("Transaction not found.");
                }
            }
            saveActivity(connection, "Transaction deleted: ID " + id);
            connection.commit();
        }
        loadPersistentState();
        return jsonResponse(200, "{\"success\":true}");
    }

    static synchronized WebResponse deleteActivityResponse(Map<String, String> data) throws SQLException {
        int id = positiveId(required(data, "id"));
        try (Connection connection = openDatabase(); PreparedStatement delete = connection.prepareStatement("DELETE FROM activity WHERE id=?")) {
            delete.setInt(1, id);
            if (delete.executeUpdate() == 0) {
                throw new IllegalArgumentException("Activity entry not found.");
            }
        }
        loadPersistentState();
        return jsonResponse(200, "{\"success\":true}");
    }

    static synchronized WebResponse setBudgetResponse(Map<String, String> data) throws SQLException {
        String category = validCategory(required(data, "category"));
        double limit = nonNegativeAmount(required(data, "limit"));
        try (Connection connection = openDatabase()) {
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement("UPDATE budgets SET amount_limit=? WHERE category=?")) {
                update.setDouble(1, limit);
                update.setString(2, category);
                if (update.executeUpdate() == 0) {
                    throw new IllegalArgumentException("Budget category not found.");
                }
            }
            saveActivity(connection, "Budget set for " + category + ": Rs." + limit);
            connection.commit();
        }
        loadPersistentState();
        return jsonResponse(200, "{\"success\":true}");
    }

    static WebResponse searchResponse(Map<String, String> query) throws SQLException {
        String keyword = query.getOrDefault("keyword", "").trim().toLowerCase();
        StringBuilder json = new StringBuilder();
        json.append("{\"transactions\":[");
        boolean first = true;
        if (!keyword.isEmpty()) {
            String pattern = "%" + keyword + "%";
            try (Connection connection = openDatabase(); PreparedStatement search = connection.prepareStatement("SELECT id, type, amount, category, description, transaction_date, income_source, payment_mode FROM transactions WHERE LOWER(category) LIKE ? OR LOWER(description) LIKE ? OR LOWER(type) LIKE ? OR LOWER(CAST(transaction_date AS TEXT)) LIKE ? ORDER BY id")) {
                for (int i = 1; i <= 4; i++) {
                    search.setString(i, pattern);
                }
                try (ResultSet rows = search.executeQuery()) {
                    while (rows.next()) {
                        Transaction transaction = transactionFromRow(rows);
                        if (!first) {
                            json.append(",");
                        }
                        json.append(transactionJson(transaction));
                        first = false;
                    }
                }
            }
        }
        json.append("]}");
        return jsonResponse(200, json.toString());
    }

    static WebResponse staticFileResponse(String requestPath) throws IOException {
        if (requestPath.equals("/")) {
            requestPath = "/index.html";
        }
        Path frontend = Paths.get("..", "frontend").toAbsolutePath().normalize();
        if (!Files.exists(frontend.resolve("index.html"))) {
            frontend = Paths.get("frontend").toAbsolutePath().normalize();
        }
        Path file = frontend.resolve(requestPath.substring(1)).normalize();
        if (!file.startsWith(frontend) || !Files.exists(file) || Files.isDirectory(file)) {
            return textResponse(404, "File not found", "text/plain");
        }
        return binaryResponse(200, Files.readAllBytes(file), contentType(file.toString()));
    }

    static String readLine(BufferedInputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int current;
        boolean seenAny = false;
        while ((current = input.read()) != -1) {
            seenAny = true;
            if (current == '\n') {
                break;
            }
            if (current != '\r') {
                buffer.write(current);
            }
        }
        if (!seenAny && buffer.size() == 0) {
            return null;
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    static class WebResponse {
        int status;
        String contentType;
        byte[] body;

        WebResponse(int status, byte[] body, String contentType) {
            this.status = status;
            this.body = body;
            this.contentType = contentType;
        }
    }

    static WebResponse jsonResponse(int status, String json) {
        return textResponse(status, json, "application/json; charset=utf-8");
    }

    static WebResponse textResponse(int status, String text, String contentType) {
        return binaryResponse(status, text.getBytes(StandardCharsets.UTF_8), contentType);
    }

    static WebResponse binaryResponse(int status, byte[] body, String contentType) {
        return new WebResponse(status, body, contentType);
    }

    static void sendResponse(OutputStream output, WebResponse response) throws IOException {
        String headers = "HTTP/1.1 " + response.status + " " + statusText(response.status) + "\r\n"
            + "Content-Type: " + response.contentType + "\r\n"
            + "Content-Length: " + response.body.length + "\r\n"
            + "Access-Control-Allow-Origin: *\r\n"
            + "Access-Control-Allow-Methods: GET,POST,OPTIONS\r\n"
            + "Access-Control-Allow-Headers: Content-Type\r\n"
            + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.UTF_8));
        output.write(response.body);
        output.flush();
    }

    static String statusText(int status) {
        if (status == 200) {
            return "OK";
        }
        if (status == 204) {
            return "No Content";
        }
        if (status == 400) {
            return "Bad Request";
        }
        if (status == 404) {
            return "Not Found";
        }
        if (status == 405) {
            return "Method Not Allowed";
        }
        return "Internal Server Error";
    }

    static Map<String, String> parseQuery(String query) {
        Map<String, String> values = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return values;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length > 1 ? decode(parts[1]) : "";
            values.put(key, value);
        }
        return values;
    }

    static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    static String required(Map<String, String> data, String key) {
        String value = data.get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing value: " + key);
        }
        return value.trim();
    }

    static double positiveAmount(String value) {
        double amount = parseAmount(value);
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        return amount;
    }

    static double nonNegativeAmount(String value) {
        double amount = parseAmount(value);
        if (amount < 0) {
            throw new IllegalArgumentException("Budget cannot be negative.");
        }
        return amount;
    }

    static double parseAmount(String value) {
        try {
            double amount = Double.parseDouble(value);
            if (!Double.isFinite(amount)) {
                throw new IllegalArgumentException("Please enter a finite amount.");
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Please enter a valid amount.");
        }
    }

    static int positiveId(String value) {
        try {
            int id = Integer.parseInt(value);
            if (id <= 0) {
                throw new IllegalArgumentException("Invalid transaction ID.");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid transaction ID.");
        }
    }

    static String validCategory(String value) {
        for (String category : categories) {
            if (category.equalsIgnoreCase(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Invalid category.");
    }

    static String validText(String value) {
        String text = value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Text value cannot be empty.");
        }
        return text;
    }

    static LocalDate validDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Please enter a valid date.");
        }
    }

    static double calculateCategoryExpense(String category) {
        double total = 0;
        for (Transaction transaction : transactions) {
            if (transaction instanceof Expense && transaction.getCategory().equalsIgnoreCase(category)) {
                total += transaction.getAmount();
            }
        }
        return total;
    }

    static double totalIncome() {
        double total = 0;
        for (Transaction transaction : transactions) {
            if (transaction instanceof Income) {
                total += transaction.getAmount();
            }
        }
        return total;
    }

    static double totalExpense() {
        double total = 0;
        for (Transaction transaction : transactions) {
            if (transaction instanceof Expense) {
                total += transaction.getAmount();
            }
        }
        return total;
    }

    static String buildStateJson() {
        StringBuilder json = new StringBuilder();
        double income = totalIncome();
        double expense = totalExpense();
        json.append("{");
        json.append("\"appName\":\"").append(escape(APP_NAME)).append("\",");
        json.append("\"categories\":").append(categoriesJson()).append(",");
        json.append("\"summary\":{\"income\":").append(income).append(",\"expense\":").append(expense).append(",\"balance\":").append(income - expense).append("},");
        json.append("\"transactions\":").append(transactionsJson()).append(",");
        json.append("\"budgets\":").append(budgetsJson()).append(",");
        json.append("\"categoryAnalysis\":").append(categoryAnalysisJson()).append(",");
        json.append("\"activity\":").append(activityJson()).append(",");
        json.append("\"info\":").append(infoJson());
        json.append("}");
        return json.toString();
    }

    static String categoriesJson() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < categories.length; i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("\"").append(escape(categories[i])).append("\"");
        }
        json.append("]");
        return json.toString();
    }

    static String transactionsJson() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < transactions.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append(transactionJson(transactions.get(i)));
        }
        json.append("]");
        return json.toString();
    }

    static String transactionJson(Transaction transaction) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":").append(transaction.getId()).append(",");
        json.append("\"amount\":").append(transaction.getAmount()).append(",");
        json.append("\"category\":\"").append(escape(transaction.getCategory())).append("\",");
        json.append("\"description\":\"").append(escape(transaction.getDescription())).append("\",");
        json.append("\"date\":\"").append(transaction.getDate()).append("\",");
        if (transaction instanceof Income) {
            Income income = (Income) transaction;
            json.append("\"type\":\"Income\",");
            json.append("\"source\":\"").append(escape(income.getSource())).append("\"");
        } else {
            Expense expense = (Expense) transaction;
            json.append("\"type\":\"Expense\",");
            json.append("\"paymentMode\":\"").append(escape(expense.getPaymentMode())).append("\"");
        }
        json.append("}");
        return json.toString();
    }

    static String budgetsJson() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < budgets.length; i++) {
            Budget budget = budgets[i];
            double spent = calculateCategoryExpense(budget.getCategory());
            double limit = budget.getLimit();
            double remaining = limit - spent;
            if (i > 0) {
                json.append(",");
            }
            json.append("{");
            json.append("\"category\":\"").append(escape(budget.getCategory())).append("\",");
            json.append("\"limit\":").append(limit).append(",");
            json.append("\"spent\":").append(spent).append(",");
            json.append("\"remaining\":").append(remaining).append(",");
            json.append("\"status\":\"").append(escape(budgetStatus(limit, spent))).append("\"");
            json.append("}");
        }
        json.append("]");
        return json.toString();
    }

    static String budgetStatus(double limit, double spent) {
        if (limit == 0) {
            return "Budget not set";
        }
        if (spent > limit) {
            return "Budget exceeded";
        }
        if (spent >= limit * 0.8) {
            return "80% or more used";
        }
        return "Within budget";
    }

    static String categoryAnalysisJson() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < categories.length; i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("{\"category\":\"").append(escape(categories[i])).append("\",\"amount\":").append(calculateCategoryExpense(categories[i])).append("}");
        }
        json.append("]");
        return json.toString();
    }

    static String monthlyJson(int year, int month) {
        double income = 0;
        double expense = 0;
        double[] categorySpending = new double[categories.length];
        for (Transaction transaction : transactions) {
            if (transaction.getDate().getYear() == year && transaction.getDate().getMonthValue() == month) {
                if (transaction instanceof Income) {
                    income += transaction.getAmount();
                } else {
                    expense += transaction.getAmount();
                    for (int i = 0; i < categories.length; i++) {
                        if (transaction.getCategory().equalsIgnoreCase(categories[i])) {
                            categorySpending[i] += transaction.getAmount();
                        }
                    }
                }
            }
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"income\":").append(income).append(",\"expense\":").append(expense).append(",\"balance\":").append(income - expense).append(",\"categorySpending\":[");
        for (int i = 0; i < categories.length; i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("{\"category\":\"").append(escape(categories[i])).append("\",\"amount\":").append(categorySpending[i]).append("}");
        }
        json.append("]}");
        return json.toString();
    }

    static String activityJson() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < activityHistory.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            ActivityEntry activity = activityHistory.get(i);
            json.append("{\"id\":").append(activity.id).append(",\"message\":\"").append(escape(activity.message)).append("\",\"createdAt\":\"").append(escape(activity.createdAt)).append("\"}");
        }
        json.append("]");
        return json.toString();
    }

    static String infoJson() {
        int descriptionWords = 0;
        for (Transaction transaction : transactions) {
            String description = transaction.getDescription().trim();
            if (!description.isEmpty()) {
                descriptionWords += description.split("\\s+").length;
            }
        }
        return "{\"appName\":\"" + escape(APP_NAME) + "\",\"categoryCount\":" + categories.length + ",\"transactionCount\":" + transactions.size() + ",\"activityCount\":" + activityHistory.size() + ",\"descriptionWords\":" + descriptionWords + ",\"applicationDate\":\"" + LocalDate.now() + "\"}";
    }

    static String errorJson(String message) {
        return "{\"success\":false,\"message\":\"" + escape(message) + "\"}";
    }

    static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    static String contentType(String file) {
        if (file.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (file.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (file.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (file.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

}
