package com.medicinevending.web.controller;

import com.medicinevending.web.model.Medicine;
import com.medicinevending.web.service.MedicineService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MedicineController {

	private final MedicineService medicineService;

	public MedicineController(MedicineService medicineService) {
		this.medicineService = medicineService;
	}

	@GetMapping({ "/", "/medicines" })
	public String listMedicines(
			@RequestParam(name = "q", required = false) String q,
			@RequestParam(name = "category", required = false) String category,
			Model model) {

		List<Medicine> allMedicines = medicineService.getAllMedicines();
		List<Medicine> medicines = medicineService.filterMedicines(allMedicines, q, category);

		model.addAttribute("medicines", medicines);
		model.addAttribute("categories", medicineService.getDistinctCategories(allMedicines));
		model.addAttribute("q", q == null ? "" : q);
		model.addAttribute("selectedCategory", category == null ? "" : category);
		model.addAttribute("resultCount", medicines.size());
		model.addAttribute("totalCount", allMedicines.size());
		return "medicines";
	}
}
