package com.medicinevending.web.repository;

import com.medicinevending.web.model.Medicine;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicineRepository extends JpaRepository<Medicine, Integer> {

	/** Loads the medicine row with a database write lock (SELECT ... FOR UPDATE) for the purchase. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Medicine m where m.medicineId = :id")
	Optional<Medicine> findByIdForUpdate(@Param("id") Integer id);

	/**
	 * Atomic, guarded stock deduction. The "stock >= :qty" condition means the row is only
	 * changed if enough stock is still there, so stock can never go negative.
	 * Returns the number of rows changed (1 = success, 0 = not enough stock).
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Medicine m set m.stock = m.stock - :qty where m.medicineId = :id and m.stock >= :qty")
	int deductStock(@Param("id") Integer id, @Param("qty") int qty);

	// ---------------------------------------------------------------- Stage 5B dashboard (read-only)

	/** SUM of stock; NULL when the table is empty. */
	@Query("select sum(m.stock) from Medicine m")
	Long sumStock();

	long countByStockLessThan(int threshold);

	List<Medicine> findByStockLessThanOrderByStockAscMedicineNameAsc(int threshold, Pageable pageable);

	/** Rows with a NULL expiry_date never match the date comparisons below. */
	long countByExpiryDateBefore(LocalDate date);

	List<Medicine> findByExpiryDateBeforeOrderByExpiryDateAsc(LocalDate date, Pageable pageable);

	long countByExpiryDateBetween(LocalDate from, LocalDate to);

	List<Medicine> findByExpiryDateBetweenOrderByExpiryDateAsc(LocalDate from, LocalDate to, Pageable pageable);

	// ---------------------------------------------------------------- Stage 5C admin stock + slot checks

	/**
	 * Atomic admin stock change computed by the database. Only applies if the result stays within
	 * 0..max, so it can never produce negative stock and never overwrites a concurrent purchase.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Medicine m set m.stock = m.stock + :delta"
			+ " where m.medicineId = :id and m.stock + :delta >= 0 and m.stock + :delta <= :max")
	int adjustStock(@Param("id") Integer id, @Param("delta") int delta, @Param("max") int max);

	/** Sets only the stock column; every other column is left alone. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Medicine m set m.stock = :stock where m.medicineId = :id")
	int setStock(@Param("id") Integer id, @Param("stock") int stock);

	boolean existsBySlotNumber(int slotNumber);

	boolean existsBySlotNumberAndMedicineIdNot(int slotNumber, Integer medicineId);
}
