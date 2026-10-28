# SMART EXPENSE & BUDGET TRACKER

A small Java OOP expense tracker with an HTML, CSS, and JavaScript interface. The Java HTTP API uses SQLite to save transactions, budgets, and activity between launches.

## Project Structure

```text
SmartExpenseBudgetTracker/
├── frontend/
│   ├── index.html
│   ├── style.css
│   ├── script.js
│   ├── favicon.svg
│   ├── privacy.html
│   └── terms.html
└── backend/
    ├── SmartExpenseBudgetTracker.java
    └── sqlite-jdbc-3.53.4.0.jar
```

## Run

Install a Java JDK, open a terminal in the `backend` folder, then compile and start the server:

```bash
javac SmartExpenseBudgetTracker.java
java --enable-native-access=ALL-UNNAMED -cp ".;sqlite-jdbc-3.53.4.0.jar" SmartExpenseBudgetTracker
```

On macOS or Linux, use `:` instead of `;` in the classpath:

```bash
java --enable-native-access=ALL-UNNAMED -cp ".:sqlite-jdbc-3.53.4.0.jar" SmartExpenseBudgetTracker
```

Open <http://localhost:8000/>. The database file `expense-tracker.db` is created beside the Java application and is retained when the server stops.

## Storage

SQLite creates three tables when the server first starts:

- `transactions` stores income and expense records.
- `budgets` stores one spending limit per category.
- `activity` stores the latest 100 activity messages.

All inserts, edits, deletes, and searches use JDBC prepared statements. Transactions, budgets, and activity are loaded from SQLite at startup, and API state/monthly views read saved values. The standard Java classes `Transaction`, `Expense`, `Income`, `Budget`, and `Trackable` continue to represent the application's data and behavior.

The database file contains the app's data. Back it up before moving or removing the project folder.
