let appState = null;
let visibleTransactions = [];

const incomeSources = ["Salary", "Allowance", "Freelancing", "Scholarship", "Other"];
const paymentModes = ["Cash", "UPI", "Debit Card", "Credit Card", "Net Banking"];
const months = [
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December"
];

const ids = {
    totalIncome: document.getElementById("totalIncome"),
    totalExpense: document.getElementById("totalExpense"),
    currentBalance: document.getElementById("currentBalance"),
    messageBox: document.getElementById("messageBox"),
    transactionForm: document.getElementById("transactionForm"),
    formTitle: document.getElementById("formTitle"),
    editId: document.getElementById("editId"),
    transactionType: document.getElementById("transactionType"),
    amount: document.getElementById("amount"),
    category: document.getElementById("category"),
    date: document.getElementById("date"),
    description: document.getElementById("description"),
    detailLabel: document.getElementById("detailLabel"),
    detailValue: document.getElementById("detailValue"),
    saveButton: document.getElementById("saveButton"),
    cancelEditButton: document.getElementById("cancelEditButton"),
    searchInput: document.getElementById("searchInput"),
    clearSearchButton: document.getElementById("clearSearchButton"),
    transactionRows: document.getElementById("transactionRows"),
    budgetForm: document.getElementById("budgetForm"),
    budgetCategory: document.getElementById("budgetCategory"),
    budgetLimit: document.getElementById("budgetLimit"),
    budgetRows: document.getElementById("budgetRows"),
    categoryAnalysis: document.getElementById("categoryAnalysis"),
    monthlyForm: document.getElementById("monthlyForm"),
    monthlyYear: document.getElementById("monthlyYear"),
    monthlyMonth: document.getElementById("monthlyMonth"),
    monthlyIncome: document.getElementById("monthlyIncome"),
    monthlyExpense: document.getElementById("monthlyExpense"),
    monthlyBalance: document.getElementById("monthlyBalance"),
    monthlyCategories: document.getElementById("monthlyCategories"),
    activityRows: document.getElementById("activityRows"),
    appName: document.getElementById("appName"),
    categoryCount: document.getElementById("categoryCount"),
    transactionCount: document.getElementById("transactionCount"),
    activityCount: document.getElementById("activityCount"),
    descriptionWords: document.getElementById("descriptionWords"),
    applicationDate: document.getElementById("applicationDate")
};

function money(value) {
    return "Rs. " + Number(value).toFixed(2);
}

function todayText() {
    return new Date().toISOString().slice(0, 10);
}

function showMessage(text, isError) {
    ids.messageBox.textContent = text;
    ids.messageBox.className = isError ? "message error" : "message";
}

function optionList(select, values) {
    select.innerHTML = "";
    values.forEach(function(value) {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        select.appendChild(option);
    });
}

function updateDetailOptions() {
    if (ids.transactionType.value === "Income") {
        ids.detailLabel.firstChild.textContent = "Income Source ";
        optionList(ids.detailValue, incomeSources);
    } else {
        ids.detailLabel.firstChild.textContent = "Payment Mode ";
        optionList(ids.detailValue, paymentModes);
    }
}

function formBody(data) {
    const params = new URLSearchParams();
    Object.keys(data).forEach(function(key) {
        params.append(key, data[key]);
    });
    return params;
}

async function apiGet(path) {
    const response = await fetch(path);
    const data = await response.json();
    if (!response.ok || data.success === false) {
        throw new Error(data.message || "Request failed");
    }
    return data;
}

async function apiPost(path, data) {
    const response = await fetch(path, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: formBody(data)
    });
    const result = await response.json();
    if (!response.ok || result.success === false) {
        throw new Error(result.message || "Request failed");
    }
    return result;
}

function renderSummary() {
    ids.totalIncome.textContent = money(appState.summary.income);
    ids.totalExpense.textContent = money(appState.summary.expense);
    ids.currentBalance.textContent = money(appState.summary.balance);
}

function transactionDetail(transaction) {
    if (transaction.type === "Income") {
        return transaction.source;
    }
    return transaction.paymentMode;
}

function renderTransactions(transactions) {
    ids.transactionRows.innerHTML = "";
    if (transactions.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");
        cell.colSpan = 8;
        cell.className = "empty-row";
        cell.textContent = "No transactions to display.";
        row.appendChild(cell);
        ids.transactionRows.appendChild(row);
        return;
    }
    transactions.forEach(function(transaction) {
        const row = document.createElement("tr");
        const values = [
            transaction.id,
            transaction.type,
            money(transaction.amount),
            transaction.category,
            transaction.description,
            transaction.date,
            transactionDetail(transaction)
        ];
        values.forEach(function(value) {
            const cell = document.createElement("td");
            cell.textContent = value;
            row.appendChild(cell);
        });
        const actionsCell = document.createElement("td");
        const actions = document.createElement("div");
        actions.className = "actions";
        const editButton = document.createElement("button");
        editButton.type = "button";
        editButton.className = "secondary";
        editButton.textContent = "Edit";
        editButton.addEventListener("click", function() {
            startEdit(transaction.id);
        });
        const deleteButton = document.createElement("button");
        deleteButton.type = "button";
        deleteButton.className = "danger";
        deleteButton.textContent = "Delete";
        deleteButton.addEventListener("click", function() {
            deleteTransaction(transaction.id);
        });
        actions.appendChild(editButton);
        actions.appendChild(deleteButton);
        actionsCell.appendChild(actions);
        row.appendChild(actionsCell);
        ids.transactionRows.appendChild(row);
    });
}

function renderBudgets() {
    ids.budgetRows.innerHTML = "";
    appState.budgets.forEach(function(budget) {
        const row = document.createElement("tr");
        const remaining = budget.limit === 0 ? 0 : budget.remaining;
        const values = [
            budget.category,
            money(budget.limit),
            money(budget.spent),
            money(remaining),
            budget.status
        ];
        values.forEach(function(value, index) {
            const cell = document.createElement("td");
            cell.textContent = value;
            if (index === 4) {
                cell.className = "status";
            }
            row.appendChild(cell);
        });
        ids.budgetRows.appendChild(row);
    });
}

function renderAnalysis(target, rows) {
    target.innerHTML = "";
    let highest = 0;
    rows.forEach(function(row) {
        if (row.amount > highest) {
            highest = row.amount;
        }
    });
    rows.forEach(function(row) {
        const item = document.createElement("div");
        item.className = "analysis-row";
        const category = document.createElement("strong");
        category.textContent = row.category;
        const track = document.createElement("div");
        track.className = "bar-track";
        const fill = document.createElement("div");
        fill.className = "bar-fill";
        const percent = highest === 0 ? 0 : Math.round((row.amount / highest) * 100);
        fill.style.width = percent + "%";
        track.appendChild(fill);
        const amount = document.createElement("span");
        amount.textContent = money(row.amount);
        item.appendChild(category);
        item.appendChild(track);
        item.appendChild(amount);
        target.appendChild(item);
    });
}

function renderActivity() {
    ids.activityRows.innerHTML = "";
    const legacyActivityFormat = appState.activity.some(function(activity) {
        return typeof activity === "string";
    });
    if (appState.activity.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");
        cell.colSpan = 4;
        cell.className = "empty-row";
        cell.textContent = "No activity has been recorded yet.";
        row.appendChild(cell);
        ids.activityRows.appendChild(row);
        return;
    }
    appState.activity.forEach(function(activity) {
        const activityId = typeof activity === "string" ? null : activity.id;
        const activityMessage = typeof activity === "string" ? activity : activity.message;
        const row = document.createElement("tr");
        const idCell = document.createElement("td");
        idCell.textContent = activityId === null ? "—" : activityId;
        row.appendChild(idCell);
        const messageCell = document.createElement("td");
        messageCell.textContent = activityMessage;
        row.appendChild(messageCell);
        const dateCell = document.createElement("td");
        dateCell.textContent = typeof activity === "string" ? "—" : formatActivityDate(activity.createdAt);
        row.appendChild(dateCell);
        const actionCell = document.createElement("td");
        actionCell.className = "activity-action-cell";
        if (Number.isInteger(activityId)) {
            const deleteButton = document.createElement("button");
            deleteButton.type = "button";
            deleteButton.className = "danger activity-delete";
            deleteButton.textContent = "Delete";
            deleteButton.setAttribute("aria-label", "Delete activity entry " + activityId);
            deleteButton.addEventListener("click", function() {
                deleteActivity(activityId);
            });
            actionCell.appendChild(deleteButton);
        } else {
            actionCell.textContent = "Restart app";
        }
        row.appendChild(actionCell);
        ids.activityRows.appendChild(row);
    });
    if (legacyActivityFormat) {
        showMessage("Restart the Java app to load activity IDs and enable deletion.", false);
    }
}

function formatActivityDate(value) {
    if (!value) {
        return "—";
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

async function deleteActivity(id) {
    try {
        await apiPost("/api/activity/delete", { id: String(id) });
        showMessage("Activity entry deleted successfully.", false);
        await loadState();
    } catch (error) {
        showMessage(error.message, true);
    }
}

function renderInfo() {
    ids.appName.textContent = appState.info.appName;
    ids.categoryCount.textContent = appState.info.categoryCount;
    ids.transactionCount.textContent = appState.info.transactionCount;
    ids.activityCount.textContent = appState.info.activityCount;
    ids.descriptionWords.textContent = appState.info.descriptionWords;
    ids.applicationDate.textContent = appState.info.applicationDate;
}

function renderState() {
    renderSummary();
    visibleTransactions = appState.transactions.slice();
    renderTransactions(visibleTransactions);
    renderBudgets();
    renderAnalysis(ids.categoryAnalysis, appState.categoryAnalysis);
    renderActivity();
    renderInfo();
}

function fillStaticControls() {
    const selectedMonth = ids.monthlyMonth.value || String(new Date().getMonth() + 1);
    optionList(ids.category, appState.categories);
    optionList(ids.budgetCategory, appState.categories);
    ids.monthlyMonth.innerHTML = "";
    months.forEach(function(month, index) {
        const option = document.createElement("option");
        option.value = String(index + 1);
        option.textContent = month;
        ids.monthlyMonth.appendChild(option);
    });
    ids.monthlyMonth.value = selectedMonth;
}

async function loadState() {
    appState = await apiGet("/api/state");
    fillStaticControls();
    renderState();
}

function resetTransactionForm() {
    ids.transactionForm.reset();
    ids.editId.value = "";
    ids.date.value = todayText();
    ids.transactionType.value = "Expense";
    updateDetailOptions();
    ids.formTitle.textContent = "Add Transaction";
    ids.saveButton.textContent = "Add Transaction";
    ids.cancelEditButton.hidden = true;
}

function readTransactionForm() {
    const amount = Number(ids.amount.value);
    const description = ids.description.value.trim();
    if (!ids.transactionType.value || amount <= 0 || !ids.category.value || !ids.date.value || description.length === 0 || !ids.detailValue.value) {
        throw new Error("Please enter valid transaction details.");
    }
    return {
        id: ids.editId.value,
        type: ids.transactionType.value,
        amount: String(amount),
        category: ids.category.value,
        description: description,
        date: ids.date.value,
        detail: ids.detailValue.value
    };
}

async function saveTransaction(event) {
    event.preventDefault();
    try {
        const data = readTransactionForm();
        if (data.id) {
            await apiPost("/api/transaction/update", data);
            showMessage("Transaction updated successfully.", false);
        } else {
            await apiPost("/api/transaction", data);
            showMessage("Transaction added successfully.", false);
        }
        resetTransactionForm();
        await loadState();
    } catch (error) {
        showMessage(error.message, true);
    }
}

function startEdit(id) {
    const transaction = appState.transactions.find(function(item) {
        return item.id === id;
    });
    if (!transaction) {
        showMessage("Transaction not found.", true);
        return;
    }
    ids.editId.value = transaction.id;
    ids.transactionType.value = transaction.type;
    updateDetailOptions();
    ids.amount.value = transaction.amount;
    ids.category.value = transaction.category;
    ids.description.value = transaction.description;
    ids.date.value = transaction.date;
    ids.detailValue.value = transactionDetail(transaction);
    ids.formTitle.textContent = "Edit Transaction";
    ids.saveButton.textContent = "Save Changes";
    ids.cancelEditButton.hidden = false;
    document.getElementById("add").scrollIntoView({ behavior: "smooth" });
}

async function deleteTransaction(id) {
    try {
        await apiPost("/api/transaction/delete", { id: String(id) });
        showMessage("Transaction deleted successfully.", false);
        await loadState();
    } catch (error) {
        showMessage(error.message, true);
    }
}

async function searchTransactions() {
    const keyword = ids.searchInput.value.trim();
    try {
        if (keyword.length === 0) {
            visibleTransactions = appState.transactions.slice();
        } else {
            const result = await apiGet("/api/search?keyword=" + encodeURIComponent(keyword));
            visibleTransactions = result.transactions;
        }
        renderTransactions(visibleTransactions);
    } catch (error) {
        showMessage(error.message, true);
    }
}

async function saveBudget(event) {
    event.preventDefault();
    const limit = Number(ids.budgetLimit.value);
    if (!ids.budgetCategory.value || limit < 0) {
        showMessage("Please enter a valid budget.", true);
        return;
    }
    try {
        await apiPost("/api/budget", {
            category: ids.budgetCategory.value,
            limit: String(limit)
        });
        ids.budgetForm.reset();
        showMessage("Budget updated successfully.", false);
        await loadState();
    } catch (error) {
        showMessage(error.message, true);
    }
}

async function showMonthlySummary(event) {
    event.preventDefault();
    const year = Number(ids.monthlyYear.value);
    const month = Number(ids.monthlyMonth.value);
    if (year < 2000 || year > 2100 || month < 1 || month > 12) {
        showMessage("Please select a valid month and year.", true);
        return;
    }
    try {
        const result = await apiGet("/api/monthly?year=" + encodeURIComponent(year) + "&month=" + encodeURIComponent(month));
        ids.monthlyIncome.textContent = money(result.income);
        ids.monthlyExpense.textContent = money(result.expense);
        ids.monthlyBalance.textContent = money(result.balance);
        renderAnalysis(ids.monthlyCategories, result.categorySpending);
    } catch (error) {
        showMessage(error.message, true);
    }
}

function initialDates() {
    const now = new Date();
    ids.date.value = todayText();
    ids.monthlyYear.value = now.getFullYear();
    ids.monthlyMonth.value = String(now.getMonth() + 1);
}

ids.transactionType.addEventListener("change", updateDetailOptions);
ids.transactionForm.addEventListener("submit", saveTransaction);
ids.cancelEditButton.addEventListener("click", resetTransactionForm);
ids.searchInput.addEventListener("input", searchTransactions);
ids.clearSearchButton.addEventListener("click", function() {
    ids.searchInput.value = "";
    renderTransactions(appState.transactions);
});
ids.budgetForm.addEventListener("submit", saveBudget);
ids.monthlyForm.addEventListener("submit", showMonthlySummary);

initialDates();
updateDetailOptions();
loadState().catch(function(error) {
    showMessage(error.message, true);
});
