package com.medicinevending.web.service;

import com.medicinevending.web.model.Medicine;
import com.medicinevending.web.repository.MedicineRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class MedicineService {

	private final MedicineRepository medicineRepository;

	public MedicineService(MedicineRepository medicineRepository) {
		this.medicineRepository = medicineRepository;
	}

	public List<Medicine> getAllMedicines() {
		return medicineRepository.findAll();
	}

	public List<String> getDistinctCategories(List<Medicine> medicines) {
		return medicines.stream()
				.map(Medicine::getCategory)
				.filter(category -> category != null && !category.isBlank())
				.map(String::trim)
				.distinct()
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
	}

	public List<Medicine> filterMedicines(List<Medicine> medicines, String nameQuery, String category) {
		String normalizedName = nameQuery == null ? "" : nameQuery.trim().toLowerCase(Locale.ROOT);
		String normalizedCategory = category == null ? "" : category.trim();

		return medicines.stream()
				.filter(medicine -> matchesName(medicine, normalizedName))
				.filter(medicine -> matchesCategory(medicine, normalizedCategory))
				.toList();
	}

	private boolean matchesName(Medicine medicine, String normalizedName) {
		if (normalizedName.isEmpty()) {
			return true;
		}
		String medicineName = medicine.getMedicineName();
		return medicineName != null && medicineName.toLowerCase(Locale.ROOT).contains(normalizedName);
	}

	private boolean matchesCategory(Medicine medicine, String normalizedCategory) {
		if (normalizedCategory.isEmpty()) {
			return true;
		}
		String medicineCategory = medicine.getCategory();
		return medicineCategory != null && medicineCategory.equalsIgnoreCase(normalizedCategory);
	}
}
