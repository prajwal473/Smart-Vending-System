# Smart Vending System for OTC Medicines and Essentials — Stage 5A/5B/5C/5D

Stage 4 (customer kiosk + simulated purchase) is unchanged. Stage 5 adds an admin login, dashboard and (5C) medicine management.

## 1. Configure the administrator (environment variables, nothing is stored in the code)

There is **no default admin account**. If these are not set, `/admin/login` simply rejects everyone.

| Variable | Meaning |
|---|---|
| `ADMIN_USERNAME` | admin username |
| `ADMIN_PASSWORD` | admin password, at least 8 characters. Hashed with BCrypt in memory at startup, never logged |
| `ADMIN_PASSWORD_HASH` | *(optional, preferred)* a ready-made BCrypt hash (`$2a$...`). Takes priority over `ADMIN_PASSWORD` |
| `LOW_STOCK_THRESHOLD` | *(optional)* low-stock limit, default 5 |
| `DB_USERNAME` / `DB_PASSWORD` | unchanged from Stage 4 |

Windows PowerShell (same window you run Maven from):
```powershell
$env:DB_PASSWORD="your-mysql-password"
$env:ADMIN_USERNAME="admin"
$env:ADMIN_PASSWORD="choose-a-strong-password"
.\mvnw.cmd spring-boot:run
```
Linux/macOS: `export ADMIN_USERNAME=admin ADMIN_PASSWORD='...' DB_PASSWORD='...'` then `./mvnw spring-boot:run`.
In IntelliJ/Eclipse/STS, add the same variables to the Run Configuration instead.

Then open http://localhost:8080/admin/login (the customer kiosk stays at http://localhost:8080/).

## 2. Database
No changes. No new tables; existing tables are only read by the dashboard. `ddl-auto=none` is kept.

## 3. Run the tests
```
.\mvnw.cmd test
```
All Stage 5 tests and the Stage 4 purchase tests use an in-memory H2 database (profile `test`), never your MySQL data.
Note: the original `MedicineVendingWebApplicationTests.contextLoads` has no test profile, so it still needs MySQL running and `DB_PASSWORD` set, exactly as before.

## 4. What the dashboard shows
- Total medicines: row count of `medicines`
- Available stock: SUM(`stock`)
- Total transactions: row count of `transactions`
- Total sales: SUM(`total_amount`). The `transactions` table has no status column and a row is only written after a successful payment, so every row is a completed sale
- Low stock: `stock` < threshold (default 5), lowest first
- Expired: `expiry_date` before today; Expiring soon: today through today+30 days; NULL expiry dates are ignored
- Recent transactions: latest 10 by transaction id

## 5. Stage 5C — Manage Medicines (login required)
Open http://localhost:8080/admin/medicines (or **Manage Medicines** in the sidebar).

| Page | URL | Notes |
|---|---|---|
| Inventory | `/admin/medicines` | all columns + Edit / Stock / Delete |
| Add | `/admin/medicines/new` | name, category, price, starting stock, slot, expiry, prescription flag |
| Edit | `/admin/medicines/{id}/edit` | details only; stock is shown read-only |
| Stock | `/admin/medicines/{id}/stock` | add/remove units (atomic) or set an exact quantity |
| Delete | button on the inventory page (POST) | blocked if the medicine has any transaction |

Rules: name <= 100 chars, category <= 50, price 0.01-99,999,999.99 with at most 2 decimals, stock 0-1,000,000,
slot 1-9999 (a slot already used by another medicine is rejected when you add or change it), expiry date required.
All changes are POST + CSRF protected. Nothing in `transactions` is ever changed or deleted.

Manual test: add a medicine, check it on the kiosk (http://localhost:8080/), buy one, then try deleting it
(it must be refused because it now has a transaction), then set its stock to 0 and confirm the kiosk shows it as out of stock.

## 6. Stage 5D — Transaction History (login required)
Open http://localhost:8080/admin/transactions (sidebar: **Transaction History**, or "View all" on the dashboard).

- Newest first (transaction date, then ID). Columns: Transaction ID, Medicine ID, Medicine, Qty, Total, Date & time, Details.
- **Search:** one box for a transaction ID (`42`, `TXN-00000042`) or part of a medicine name (case-insensitive). `%` and `_` are matched literally.
- **Dates:** From / To. The To date includes the whole day (up to 23:59:59). Either may be left empty. An invalid date or a start after the end shows an error and **no rows** (never unfiltered data).
- **Paging:** 10 / 25 / 50 / 100 rows per page, with page links that keep your filters; an out-of-range page is moved to the last page.
- **Summary:** "Matching transactions" and "Total sales" cover **all** rows matching the current filters, not just the visible page.
- **Details:** `/admin/transactions/{id}` shows the real stored fields only; an unknown ID shows "Transaction not found" (HTTP 404).
- Read-only: nothing on these pages changes data. The `transactions` table has no status column; only successful purchases are recorded.

Manual test: buy a few items on the kiosk (http://localhost:8080/), open Transaction History, search by the medicine name and by a TXN reference,
set From/To to today, switch to 10 rows per page, then open a transaction's Details and try `/admin/transactions/999999`.
