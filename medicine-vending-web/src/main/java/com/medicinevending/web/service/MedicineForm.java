package com.medicinevending.web.service;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Add/edit form for a medicine. Limits mirror the existing columns:
 * medicine_name VARCHAR(100), category VARCHAR(50), price DECIMAL(10,2), stock INT, slot_number INT.
 * The medicine id is never bound from the form. Stock is only used when ADDING; stock of an existing
 * medicine is changed only through the dedicated stock actions so an edit can never overwrite it.
 */
public class MedicineForm {

	public static final int MAX_STOCK = 1_000_000;

	@NotBlank(message = "Enter the medicine name.")
	@Size(max = 100, message = "The name can be at most 100 characters.")
	private String medicineName;

	@NotBlank(message = "Enter a category.")
	@Size(max = 50, message = "The category can be at most 50 characters.")
	private String category;

	@NotNull(message = "Enter the price.")
	@DecimalMin(value = "0.01", message = "The price must be greater than zero.")
	@DecimalMax(value = "99999999.99", message = "The price is too large.")
	@Digits(integer = 8, fraction = 2, message = "Use at most 2 decimal places (for example 25.50).")
	private BigDecimal price;

	@Min(value = 0, message = "Stock cannot be negative.")
	@Max(value = MAX_STOCK, message = "Stock cannot be more than 1,000,000.")
	private Integer stock;

	@NotNull(message = "Enter the slot number.")
	@Min(value = 1, message = "The slot number must be 1 or more.")
	@Max(value = 9999, message = "The slot number must be 9999 or less.")
	private Integer slotNumber;

	@NotNull(message = "Choose the expiry date.")
	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate expiryDate;

	private boolean prescriptionRequired;

	public String getMedicineName() { return medicineName; }
	public void setMedicineName(String medicineName) { this.medicineName = medicineName; }
	public String getCategory() { return category; }
	public void setCategory(String category) { this.category = category; }
	public BigDecimal getPrice() { return price; }
	public void setPrice(BigDecimal price) { this.price = price; }
	public Integer getStock() { return stock; }
	public void setStock(Integer stock) { this.stock = stock; }
	public Integer getSlotNumber() { return slotNumber; }
	public void setSlotNumber(Integer slotNumber) { this.slotNumber = slotNumber; }
	public LocalDate getExpiryDate() { return expiryDate; }
	public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
	public boolean isPrescriptionRequired() { return prescriptionRequired; }
	public void setPrescriptionRequired(boolean prescriptionRequired) { this.prescriptionRequired = prescriptionRequired; }
}
