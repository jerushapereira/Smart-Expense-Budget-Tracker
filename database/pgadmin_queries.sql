SELECT id, type, amount, category, description, transaction_date, income_source, payment_mode, created_at, updated_at
FROM public.transactions
ORDER BY id;

SELECT * FROM public.budgets ORDER BY category;

SELECT id, message, created_at
FROM public.activity
ORDER BY id DESC;

SELECT
    category,
    amount_limit,
    COALESCE(SUM(CASE WHEN transactions.type = 'Expense' THEN transactions.amount ELSE 0 END), 0) AS spent,
    amount_limit - COALESCE(SUM(CASE WHEN transactions.type = 'Expense' THEN transactions.amount ELSE 0 END), 0) AS remaining
FROM public.budgets
LEFT JOIN public.transactions USING (category)
GROUP BY category, amount_limit
ORDER BY category;
