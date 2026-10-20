package com.medicinevending.web.service;

import com.medicinevending.web.model.Medicine;
import com.medicinevending.web.repository.MedicineRepository;
import com.medicinevending.web.repository.SaleTransactionRepository;
import com.medicinevending.web.service.MedicineAdminException.Reason;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stage 5C: admin medicine management.
 *
 * Concurrency: purchases lock the medicine row (SELECT ... FOR UPDATE) and deduct stock with a guarded
 * atomic UPDATE. Here, detail edits and deletes take the same row lock, and stock changes are single atomic
 * UPDATE statements computed by the database (stock = stock + delta, never "read, add in Java, write back"),
 * so an admin change and a purchase can never overwrite each other and stock can never go negative.
 */
@Service
public class MedicineAdminService {

	private static final Logger log = LoggerFactory.getLogger(MedicineAdminService.class);
	private static final LocalDate EARLIEST_EXPIRY = LocalDate.of(2000, 1, 1);
	private static final int MAX_YEARS_AHEAD = 20;

	private final MedicineRepository medicineRepository;
	private final SaleTransactionRepository transactionRepository;
	private final Validator validator;

	public MedicineAdminService(
			MedicineRepository medicineRepository,
			SaleTransactionRepository transactionRepository,
			Validator validator) {
		this.medicineRepository = medicineRepository;
		this.transactionRepository = transactionRepository;
		this.validator = validator;
	}

	@Transactional(readOnly = true)
	public List<Medicine> listAll() {
		return medicineRepository.findAll(Sort.by(Sort.Direction.ASC, "medicineId"));
	}

	@Transactional(readOnly = true)
	public Optional<Medicine> find(Integer id) {
		if (id == null || id < 1) {
			return Optional.empty();
		}
		return medicineRepository.findById(id);
	}

	/**
	 * Rules that annotations cannot express. Returns field name -> message (empty = fine).
	 * excludeId is the medicine being edited, or null when adding.
	 */
	@Transactional(readOnly = true)
	public Map<String, String> extraRules(MedicineForm f, Integer excludeId) {
		Map<String, String> errors = new LinkedHashMap<>();
		boolean adding = excludeId == null;

		if (adding && f.getStock() == null) {
			errors.put("stock", "Enter the starting stock (0 or more).");
		}
		LocalDate expiry = f.getExpiryDate();
		if (expiry != null && (expiry.isBefore(EARLIEST_EXPIRY) || expiry.isAfter(LocalDate.now().plusYears(MAX_YEARS_AHEAD)))) {
			errors.put("expiryDate", "Enter a realistic expiry date (from 2000 up to " + MAX_YEARS_AHEAD + " years from today).");
		}
		Integer slot = f.getSlotNumber();
		if (slot != null && slot >= 1) {
			boolean taken;
			if (adding) {
				taken = medicineRepository.existsBySlotNumber(slot);
			} else {
				// Only check when the slot is actually being changed, so old records that already share a
				// slot can still have their other details edited.
				Optional<Medicine> current = medicineRepository.findById(excludeId);
				taken = current.isPresent() && current.get().getSlotNumber() != slot
						&& medicineRepository.existsBySlotNumberAndMedicineIdNot(slot, excludeId);
			}
			if (taken) {
				errors.put("slotNumber", "Slot " + slot + " is already used by another medicine.");
			}
		}
		return errors;
	}

	@Transactional
	public Medicine create(MedicineForm form) {
		requireValid(form, null);
		Medicine medicine = new Medicine();
		applyDetails(form, medicine);
		medicine.setStock(form.getStock());
		Medicine saved = medicineRepository.saveAndFlush(medicine);
		log.info("Admin added medicine id={} slot={}", saved.getMedicineId(), saved.getSlotNumber());
		return saved;
	}

	/** Updates details only. Stock is deliberately NOT touched here. */
	@Transactional
	public Medicine update(Integer id, MedicineForm form) {
		requireId(id);
		Medicine medicine = medicineRepository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
		requireValid(form, id);
		applyDetails(form, medicine);
		Medicine saved = medicineRepository.saveAndFlush(medicine);
		log.info("Admin updated medicine id={}", id);
		return saved;
	}

	/** Deletes only a medicine with no transaction history. Transaction rows are never touched. */
	@Transactional
	public void delete(Integer id) {
		requireId(id);
		// Row lock first: an in-flight purchase finishes (and records its transaction) before we look.
		Medicine medicine = medicineRepository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
		if (transactionRepository.existsByMedicineId(id)) {
			throw blocked(medicine);
		}
		try {
			medicineRepository.delete(medicine);
			medicineRepository.flush();
		} catch (DataIntegrityViolationException ex) {
			// e.g. a foreign key in the real MySQL schema; the whole transaction is rolled back.
			throw blocked(medicine);
		}
		log.info("Admin deleted medicine id={}", id);
	}

	/** Adds (positive) or removes (negative) units atomically. Returns the new stock. */
	@Transactional
	public int adjustStock(Integer id, Integer delta) {
		requireId(id);
		if (delta == null || delta == 0 || Math.abs((long) delta) > MedicineForm.MAX_STOCK) {
			throw new MedicineAdminException(Reason.INVALID,
					"Enter a whole number of units to add or remove (not zero, at most 1,000,000).");
		}
		int changed = medicineRepository.adjustStock(id, delta, MedicineForm.MAX_STOCK);
		Medicine medicine = medicineRepository.findById(id).orElseThrow(() -> notFound(id));
		if (changed != 1) {
			if (delta < 0) {
				throw new MedicineAdminException(Reason.STOCK_CONFLICT, "Only " + medicine.getStock() + " in stock, so "
						+ (-delta) + " cannot be removed. Stock cannot go below zero.");
			}
			throw new MedicineAdminException(Reason.STOCK_CONFLICT,
					"That would take stock above " + MedicineForm.MAX_STOCK + ". Current stock is " + medicine.getStock() + ".");
		}
		log.info("Admin adjusted stock of medicine id={} by {} -> {}", id, delta, medicine.getStock());
		return medicine.getStock();
	}

	/** Sets stock to an exact value (0 or more) with one UPDATE that changes nothing else. */
	@Transactional
	public int setStock(Integer id, Integer stock) {
		requireId(id);
		if (stock == null || stock < 0 || stock > MedicineForm.MAX_STOCK) {
			throw new MedicineAdminException(Reason.INVALID,
					"Stock must be a whole number from 0 to 1,000,000.");
		}
		medicineRepository.setStock(id, stock);
		if (!medicineRepository.existsById(id)) {
			throw notFound(id);
		}
		log.info("Admin set stock of medicine id={} to {}", id, stock);
		return stock;
	}

	// ------------------------------------------------------------------ helpers

	private void requireValid(MedicineForm form, Integer excludeId) {
		if (form == null) {
			throw new MedicineAdminException(Reason.INVALID, "No medicine details were submitted.");
		}
		Set<ConstraintViolation<MedicineForm>> violations = validator.validate(form);
		if (!violations.isEmpty()) {
			throw new MedicineAdminException(Reason.INVALID, violations.iterator().next().getMessage());
		}
		Map<String, String> extra = extraRules(form, excludeId);
		if (!extra.isEmpty()) {
			throw new MedicineAdminException(Reason.INVALID, extra.values().iterator().next());
		}
	}

	private static void applyDetails(MedicineForm f, Medicine m) {
		m.setMedicineName(f.getMedicineName().trim());
		m.setCategory(f.getCategory().trim());
		m.setPrice(f.getPrice().setScale(2, RoundingMode.HALF_UP).doubleValue());
		m.setSlotNumber(f.getSlotNumber());
		m.setExpiryDate(f.getExpiryDate());
		m.setPrescriptionRequired(f.isPrescriptionRequired());
	}

	private static void requireId(Integer id) {
		if (id == null || id < 1) {
			throw new MedicineAdminException(Reason.NOT_FOUND, "That medicine could not be found.");
		}
	}

	private static MedicineAdminException notFound(Integer id) {
		return new MedicineAdminException(Reason.NOT_FOUND, "Medicine #" + id + " could not be found. It may have been deleted.");
	}

	private static MedicineAdminException blocked(Medicine m) {
		return new MedicineAdminException(Reason.DELETE_BLOCKED, m.getMedicineName()
				+ " cannot be deleted because it has sales (transaction) records. Those records are kept so your history stays accurate."
				+ " To stop selling it, set its stock to 0 instead.");
	}
}
