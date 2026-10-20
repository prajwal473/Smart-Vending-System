package com.medicinevending.web.dashboard;

import com.medicinevending.web.model.Medicine;
import com.medicinevending.web.model.SaleTransaction;
import java.math.BigDecimal;
import java.util.List;

/** Read-only snapshot shown on the admin dashboard. Counts are exact; lists may be capped for display. */
public record DashboardData(
		long totalMedicines,
		long totalStock,
		long totalTransactions,
		BigDecimal totalSales,
		int lowStockThreshold,
		int expiryWarningDays,
		long lowStockCount,
		List<Medicine> lowStock,
		long expiredCount,
		List<Medicine> expired,
		long expiringSoonCount,
		List<Medicine> expiringSoon,
		List<SaleTransaction> recentTransactions) {
}
