package com.medicinevending.web.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A server-calculated price quote. Nothing in here ever comes from the browser except the
 * medicine id and quantity that were validated. (Plain getters so Thymeleaf can read every property.)
 */
public class PurchaseQuote {

	private final Integer medicineId;
	private final String medicineName;
	private final String category;
	private final BigDecimal unitPrice;
	private final int quantity;
	private final BigDecimal totalAmount;
	private final int availableStock;
	private final LocalDate expiryDate;

	public PurchaseQuote(Integer medicineId, String medicineName, String category, BigDecimal unitPrice,
			int quantity, BigDecimal totalAmount, int availableStock, LocalDate expiryDate) {
		this.medicineId = medicineId;
		this.medicineName = medicineName;
		this.category = category;
		this.unitPrice = unitPrice;
		this.quantity = quantity;
		this.totalAmount = totalAmount;
		this.availableStock = availableStock;
		this.expiryDate = expiryDate;
	}

	public Integer getMedicineId() {
		return medicineId;
	}

	public String getMedicineName() {
		return medicineName;
	}

	public String getCategory() {
		return category;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public int getAvailableStock() {
		return availableStock;
	}

	public LocalDate getExpiryDate() {
		return expiryDate;
	}
}
