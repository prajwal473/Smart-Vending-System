package com.medicinevending.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Maps the EXISTING "transactions" table (the same one the Swing application writes to).
 * Only the columns the Swing TransactionDAO already uses are mapped, so no schema change is needed.
 */
@Entity
@Table(name = "transactions")
public class SaleTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "transaction_id")
	private Integer transactionId;

	@Column(name = "medicine_id")
	private Integer medicineId;

	@Column(name = "medicine_name")
	private String medicineName;

	@Column(name = "quantity")
	private int quantity;

	@Column(name = "total_amount")
	private BigDecimal totalAmount;

	// Filled in by the database (the Swing app also inserts without it), so it is read-only here.
	@Column(name = "transaction_date", insertable = false, updatable = false)
	private LocalDateTime transactionDate;

	protected SaleTransaction() {
	}

	public SaleTransaction(Integer medicineId, String medicineName, int quantity, BigDecimal totalAmount) {
		this.medicineId = medicineId;
		this.medicineName = medicineName;
		this.quantity = quantity;
		this.totalAmount = totalAmount;
	}

	public Integer getTransactionId() {
		return transactionId;
	}

	public Integer getMedicineId() {
		return medicineId;
	}

	public String getMedicineName() {
		return medicineName;
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public LocalDateTime getTransactionDate() {
		return transactionDate;
	}

	/** Receipt reference derived from the primary key, e.g. TXN-00000042. No extra column required. */
	public String getTransactionReference() {
		return referenceFor(transactionId);
	}

	public static String referenceFor(Integer transactionId) {
		return transactionId == null ? "TXN-PENDING" : String.format("TXN-%08d", transactionId);
	}

	/** Unit price at the time of sale, derived from the stored total. */
	public BigDecimal getUnitPrice() {
		if (totalAmount == null || quantity <= 0) {
			return BigDecimal.ZERO;
		}
		return totalAmount.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
	}
}
