package com.medicinevending.web.dashboard;

import com.medicinevending.web.repository.MedicineRepository;
import com.medicinevending.web.repository.SaleTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only statistics. Uses database aggregates; never changes stock or creates transactions. */
@Service
public class DashboardService {

	private final MedicineRepository medicineRepository;
	private final SaleTransactionRepository transactionRepository;
	private final int lowStockThreshold;
	private final int expiryWarningDays;
	private final int listLimit;
	private final int recentCount;

	public DashboardService(
			MedicineRepository medicineRepository,
			SaleTransactionRepository transactionRepository,
			@Value("${app.dashboard.low-stock-threshold:5}") int lowStockThreshold,
			@Value("${app.dashboard.expiry-warning-days:30}") int expiryWarningDays,
			@Value("${app.dashboard.list-limit:50}") int listLimit,
			@Value("${app.dashboard.recent-transactions:10}") int recentCount) {
		this.medicineRepository = medicineRepository;
		this.transactionRepository = transactionRepository;
		this.lowStockThreshold = lowStockThreshold;
		this.expiryWarningDays = expiryWarningDays;
		this.listLimit = Math.max(1, listLimit);
		this.recentCount = Math.max(1, recentCount);
	}

	@Transactional(readOnly = true)
	public DashboardData load() {
		LocalDate today = LocalDate.now();
		LocalDate soonLimit = today.plusDays(expiryWarningDays);
		Pageable listPage = PageRequest.of(0, listLimit);

		// Sum of an empty table is NULL in SQL, so every aggregate is null-checked.
		Long stock = medicineRepository.sumStock();
		BigDecimal sales = transactionRepository.sumTotalAmount();

		// The transactions table has no status column and PurchaseService only inserts a row after a
		// successful payment (failed payments change nothing), so every row is a completed sale.
		return new DashboardData(
				medicineRepository.count(),
				stock == null ? 0L : stock,
				transactionRepository.count(),
				sales == null ? BigDecimal.ZERO : sales,
				lowStockThreshold,
				expiryWarningDays,
				medicineRepository.countByStockLessThan(lowStockThreshold),
				medicineRepository.findByStockLessThanOrderByStockAscMedicineNameAsc(lowStockThreshold, listPage),
				medicineRepository.countByExpiryDateBefore(today),
				medicineRepository.findByExpiryDateBeforeOrderByExpiryDateAsc(today, listPage),
				medicineRepository.countByExpiryDateBetween(today, soonLimit),
				medicineRepository.findByExpiryDateBetweenOrderByExpiryDateAsc(today, soonLimit, listPage),
				transactionRepository.findAll(
						PageRequest.of(0, recentCount, Sort.by(Sort.Direction.DESC, "transactionId"))).getContent());
	}
}
