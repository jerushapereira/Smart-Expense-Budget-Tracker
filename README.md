# SMART EXPENSE & BUDGET TRACKER

This project keeps the existing Java backend and HTML, CSS, and JavaScript interface. PostgreSQL stores transactions, category budgets, and activity history. The JDBC driver is included in `backend`.

## 1. Create the PostgreSQL database

Open pgAdmin 4 and connect to your local PostgreSQL server. Right-click **Databases**, choose **Create > Database**, and enter:

```text
smart_expense_budget_tracker
```

Alternatively, open Query Tool on the built-in `postgres` database and run [`database/create_database.sql`](database/create_database.sql).

Use the PostgreSQL account that can create tables in this database. When the Java app starts, it creates the `transactions`, `budgets`, and `activity` tables and inserts the five default budget categories.

## 2. Set the connection details and run

Open PowerShell in `D:\Smart-Expense-Budget-Tracker\backend` and run:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/smart_expense_budget_tracker"
$env:DB_USER = "postgres"
$securePassword = Read-Host "PostgreSQL password" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $securePassword).Password
javac -cp ".;postgresql-42.7.14.jar" SmartExpenseBudgetTracker.java
java -cp ".;postgresql-42.7.14.jar" SmartExpenseBudgetTracker
```

Use the username and password for your PostgreSQL server. The password is read from the current PowerShell session and is not saved in the project. The app uses port `8000`; open <http://localhost:8000/> after it starts.

Optional environment variables:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/your_database_name"
$env:DB_USER = "your_postgres_user"
```

The default URL and user are `jdbc:postgresql://localhost:5432/smart_expense_budget_tracker` and `postgres`.

## 3. View app data in pgAdmin 4

Start the Java app once so it creates the tables. In pgAdmin, select the `smart_expense_budget_tracker` database, open **Tools > Query Tool**, and run the queries in [`database/pgadmin_queries.sql`](database/pgadmin_queries.sql). You can also expand **Schemas > public > Tables** to browse and edit the rows.

```sql
SELECT * FROM public.transactions ORDER BY id;
SELECT * FROM public.budgets ORDER BY category;
SELECT * FROM public.activity ORDER BY id DESC;
```

## Database tables

- `transactions`: income and expense entries, including date and category.
- `budgets`: one spending limit for each category.
- `activity`: saved add, edit, delete, and budget-change events with timestamps.

The transaction table has separate `income_source` and `payment_mode` columns, plus `created_at` and `updated_at` timestamps. On startup, the app migrates the earlier `detail` column into the correct source or payment column. Deleting a transaction runs `DELETE FROM transactions WHERE id = ?`; the activity log keeps a separate record that the delete occurred.

The Java API uses JDBC prepared statements for inserts, reads, updates, deletes, and search. PostgreSQL is the source of saved state, so data remains available after restarting the Java app.
