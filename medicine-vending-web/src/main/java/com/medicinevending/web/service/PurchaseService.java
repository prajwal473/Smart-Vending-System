package com.medicinevending.web.service;

import com.medicinevending.web.model.Medicine;
import com.medicinevending.web.model.SaleTransaction;
import com.medicinevending.web.repository.MedicineRepository;
import com.medicinevending.web.repository.SaleTransactionRepository;
import com.medicinevending.web.service.PaymentSimulationService.PaymentResult;
import com.medicinevending.web.service.PurchaseRejectedException.Reason;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseService {

	private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

	/** Result of a completed attempt. If paymentSucceeded is false nothing was changed in the database. */
	public record PurchaseOutcome(
			boolean paymentSucceeded,
			PurchaseQuote quote,
			Integer transactionId,
			String paymentMessage) {
	}

	private final MedicineRepository medicineRepository;
	private final SaleTransactionRepository saleTransactionRepository;
	private final PaymentSimulationService paymentSimulationService;

	public PurchaseService(
			MedicineRepository medicineRepository,
			SaleTransactionRepository saleTransactionRepository,
			PaymentSimulationService paymentSimulationService) {
		this.medicineRepository = medicineRepository;
		this.saleTransactionRepository = saleTransactionRepository;
		this.paymentSimulationService = paymentSimulationService;
	}

	/** Validates the request against the database and returns a server-calculated quote. Changes nothing. */
	@Transactional(readOnly = true)
	public PurchaseQuote prepareQuote(Integer medicineId, Integer quantity) {
		requireValidRequest(medicineId, quantity);
		Medicine medicine = medicineRepository.findById(medicineId)
				.orElseThrow(PurchaseService::notFound);
		return validateAndQuote(medicine, quantity);
	}

	/**
	 * Re-validates under a row lock, runs the simulated payment, and ONLY if it succeeds
	 * deducts stock and records the transaction. All database changes are in one transaction:
	 * if anything fails, stock and the transaction table are both rolled back.
	 */
	@Transactional
	public PurchaseOutcome completePurchase(
			Integer medicineId,
			Integer quantity,
			PaymentSimulationService.Mode paymentMode) {

		requireValidRequest(medicineId, quantity);

		// Lock the medicine row so two simultaneous buyers are processed one after the other.
		Medicine medicine = medicineRepository.findByIdForUpdate(medicineId)
				.orElseThrow(PurchaseService::notFound);

		// Validate again with the freshest data (stock/expiry may have changed since the confirm page).
		PurchaseQuote quote = validateAndQuote(medicine, quantity);

		PaymentResult payment = paymentSimulationService.process(paymentMode, quote.getTotalAmount());
		if (!payment.successful()) {
			log.info("Purchase not completed (payment failed) for medicine {}", medicineId);
			return new PurchaseOutcome(false, quote, null, payment.message());
		}

		// Guarded atomic deduction: only succeeds if enough stock is still present.
		int updated = medicineRepository.deductStock(medicineId, quote.getQuantity());
		if (updated != 1) {
			// Throwing rolls the transaction back. In a real system this is where the payment would be voided.
			throw new PurchaseRejectedException(Reason.INSUFFICIENT_STOCK,
					"Sorry, the stock changed while you were paying. Nothing has been charged.");
		}

		SaleTransaction saved = saleTransactionRepository.saveAndFlush(new SaleTransaction(
				quote.getMedicineId(),
				quote.getMedicineName(),
				quote.getQuantity(),
				quote.getTotalAmount()));

		log.info("Purchase completed: {} medicine={} qty={} total={}",
				saved.getTransactionReference(), quote.getMedicineId(), quote.getQuantity(), quote.getTotalAmount());
		return new PurchaseOutcome(true, quote, saved.getTransactionId(), payment.message());
	}

	@Transactional(readOnly = true)
	public Optional<SaleTransaction> findReceipt(Integer transactionId) {
		if (transactionId == null) {
			return Optional.empty();
		}
		return saleTransactionRepository.findById(transactionId);
	}

	// ------------------------------------------------------------------ validation

	private static void requireValidRequest(Integer medicineId, Integer quantity) {
		if (medicineId == null || medicineId < 1) {
			throw new PurchaseRejectedException(Reason.INVALID_REQUEST, "No valid medicine was selected.");
		}
		if (quantity == null || quantity < 1) {
			throw new PurchaseRejectedException(Reason.INVALID_REQUEST,
					"Please choose a quantity of at least 1.");
		}
	}

	private static PurchaseRejectedException notFound() {
		return new PurchaseRejectedException(Reason.MEDICINE_NOT_FOUND,
				"That medicine could not be found in this machine.");
	}

	private PurchaseQuote validateAndQuote(Medicine medicine, int quantity) {
		String name = medicine.getMedicineName();

		if (medicine.isPrescriptionRequired()) {
			throw new PurchaseRejectedException(Reason.PRESCRIPTION_REQUIRED,
					name + " requires a valid prescription and cannot be sold from this kiosk.");
		}
		if (medicine.getExpiryDate() == null || medicine.isExpired()) {
			throw new PurchaseRejectedException(Reason.EXPIRED,
					name + " has expired (or has no valid expiry date) and cannot be purchased.");
		}
		if (medicine.getStock() <= 0) {
			throw new PurchaseRejectedException(Reason.OUT_OF_STOCK,
					name + " is out of stock.");
		}
		if (quantity > medicine.getStock()) {
			throw new PurchaseRejectedException(Reason.INSUFFICIENT_STOCK,
					"Only " + medicine.getStock() + " of " + name + " left in stock. Please choose a smaller quantity.");
		}
		double rawPrice = medicine.getPrice();
		if (!Double.isFinite(rawPrice) || rawPrice <= 0) {
			throw new PurchaseRejectedException(Reason.PRICE_UNAVAILABLE,
					"The price for " + name + " is not available, so it cannot be sold.");
		}

		// Price and total are ALWAYS calculated here from the database price, never taken from the browser.
		BigDecimal unitPrice = BigDecimal.valueOf(rawPrice).setScale(2, RoundingMode.HALF_UP);
		BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));

		return new PurchaseQuote(
				medicine.getMedicineId(),
				name,
				medicine.getCategory(),
				unitPrice,
				quantity,
				total,
				medicine.getStock(),
				medicine.getExpiryDate());
	}
}
