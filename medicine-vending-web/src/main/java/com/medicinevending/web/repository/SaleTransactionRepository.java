package com.medicinevending.web.repository;

import com.medicinevending.web.model.SaleTransaction;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SaleTransactionRepository extends JpaRepository<SaleTransaction, Integer> {

	/** SUM of total_amount; NULL when there are no transactions. Read-only (Stage 5B dashboard). */
	@Query("select sum(t.totalAmount) from SaleTransaction t")
	BigDecimal sumTotalAmount();

	/** True if any sale references this medicine (used to block unsafe deletes). */
	boolean existsByMedicineId(Integer medicineId);
}
