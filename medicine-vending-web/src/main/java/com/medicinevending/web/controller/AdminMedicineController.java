package com.medicinevending.web.controller;

import com.medicinevending.web.model.Medicine;
import com.medicinevending.web.service.MedicineAdminException;
import com.medicinevending.web.service.MedicineAdminService;
import com.medicinevending.web.service.MedicineForm;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Stage 5C. Every route here is under /admin/** so SecurityConfig already requires the ADMIN role and
 * CSRF tokens on all POSTs. State changes are POST-then-redirect (no duplicate submit on refresh).
 */
@Controller
@RequestMapping("/admin/medicines")
public class AdminMedicineController {

	private static final String LIST = "redirect:/admin/medicines";

	private final MedicineAdminService service;

	public AdminMedicineController(MedicineAdminService service) {
		this.service = service;
	}

	// ------------------------------------------------------------------ inventory

	@GetMapping
	public String inventory(Principal principal, Model model) {
		model.addAttribute("adminName", name(principal));
		model.addAttribute("medicines", service.listAll());
		return "admin/medicines";
	}

	// ------------------------------------------------------------------ add

	@GetMapping("/new")
	public String newForm(Principal principal, Model model) {
		model.addAttribute("form", new MedicineForm());
		return addView(principal, model);
	}

	@PostMapping("/new")
	public String create(
			@Valid @ModelAttribute("form") MedicineForm form,
			BindingResult result,
			Principal principal,
			Model model,
			RedirectAttributes redirect) {

		addExtraErrors(result, service.extraRules(form, null));
		if (result.hasErrors()) {
			return addView(principal, model);
		}
		try {
			Medicine saved = service.create(form);
			redirect.addFlashAttribute("flashSuccess", "Added " + saved.getMedicineName() + " (ID " + saved.getMedicineId() + ").");
			return LIST;
		} catch (MedicineAdminException ex) {
			model.addAttribute("flashError", ex.getMessage());
			return addView(principal, model);
		}
	}

	// ------------------------------------------------------------------ edit

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable("id") Integer id, Principal principal, Model model, RedirectAttributes redirect) {
		Optional<Medicine> medicine = service.find(id);
		if (medicine.isEmpty()) {
			return missing(id, redirect);
		}
		model.addAttribute("form", toForm(medicine.get()));
		return editView(medicine.get(), principal, model);
	}

	@PostMapping("/{id}/edit")
	public String update(
			@PathVariable("id") Integer id,
			@Valid @ModelAttribute("form") MedicineForm form,
			BindingResult result,
			Principal principal,
			Model model,
			RedirectAttributes redirect) {

		Optional<Medicine> medicine = service.find(id);
		if (medicine.isEmpty()) {
			return missing(id, redirect);
		}
		addExtraErrors(result, service.extraRules(form, id));
		if (result.hasErrors()) {
			return editView(medicine.get(), principal, model);
		}
		try {
			Medicine saved = service.update(id, form);
			redirect.addFlashAttribute("flashSuccess", "Saved changes to " + saved.getMedicineName() + ".");
			return LIST;
		} catch (MedicineAdminException ex) {
			if (ex.getReason() == MedicineAdminException.Reason.NOT_FOUND) {
				redirect.addFlashAttribute("flashError", ex.getMessage());
				return LIST;
			}
			model.addAttribute("flashError", ex.getMessage());
			return editView(medicine.get(), principal, model);
		}
	}

	// ------------------------------------------------------------------ delete

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable("id") Integer id, RedirectAttributes redirect) {
		try {
			Optional<Medicine> medicine = service.find(id);
			service.delete(id);
			redirect.addFlashAttribute("flashSuccess",
					"Deleted " + medicine.map(Medicine::getMedicineName).orElse("medicine #" + id) + ".");
		} catch (MedicineAdminException ex) {
			redirect.addFlashAttribute("flashError", ex.getMessage());
		}
		return LIST;
	}

	// ------------------------------------------------------------------ stock

	@GetMapping("/{id}/stock")
	public String stockPage(@PathVariable("id") Integer id, Principal principal, Model model, RedirectAttributes redirect) {
		Optional<Medicine> medicine = service.find(id);
		if (medicine.isEmpty()) {
			return missing(id, redirect);
		}
		model.addAttribute("adminName", name(principal));
		model.addAttribute("medicine", medicine.get());
		return "admin/medicine-stock";
	}

	@PostMapping("/{id}/stock/adjust")
	public String adjustStock(
			@PathVariable("id") Integer id,
			@RequestParam(name = "delta", required = false) String delta,
			RedirectAttributes redirect) {
		return stockAction(id, redirect, () -> {
			int now = service.adjustStock(id, parseInt(delta));
			return "Stock updated. There are now " + now + " in stock.";
		});
	}

	@PostMapping("/{id}/stock/set")
	public String setStock(
			@PathVariable("id") Integer id,
			@RequestParam(name = "stock", required = false) String stock,
			RedirectAttributes redirect) {
		return stockAction(id, redirect, () -> {
			int now = service.setStock(id, parseInt(stock));
			return "Stock set to " + now + ".";
		});
	}

	// ------------------------------------------------------------------ helpers

	private String stockAction(Integer id, RedirectAttributes redirect, java.util.function.Supplier<String> action) {
		try {
			redirect.addFlashAttribute("flashSuccess", action.get());
		} catch (MedicineAdminException ex) {
			if (ex.getReason() == MedicineAdminException.Reason.NOT_FOUND) {
				redirect.addFlashAttribute("flashError", ex.getMessage());
				return LIST;
			}
			redirect.addFlashAttribute("flashError", ex.getMessage());
		}
		return "redirect:/admin/medicines/" + id + "/stock";
	}

	private String missing(Integer id, RedirectAttributes redirect) {
		redirect.addFlashAttribute("flashError", "Medicine #" + id + " could not be found. It may have been deleted.");
		return LIST;
	}

	private String addView(Principal principal, Model model) {
		model.addAttribute("adminName", name(principal));
		model.addAttribute("editing", false);
		model.addAttribute("formTitle", "Add medicine");
		model.addAttribute("formAction", "/admin/medicines/new");
		return "admin/medicine-form";
	}

	private String editView(Medicine medicine, Principal principal, Model model) {
		model.addAttribute("adminName", name(principal));
		model.addAttribute("editing", true);
		model.addAttribute("medicine", medicine);
		model.addAttribute("formTitle", "Edit medicine #" + medicine.getMedicineId());
		model.addAttribute("formAction", "/admin/medicines/" + medicine.getMedicineId() + "/edit");
		return "admin/medicine-form";
	}

	private static MedicineForm toForm(Medicine m) {
		MedicineForm f = new MedicineForm();
		f.setMedicineName(m.getMedicineName());
		f.setCategory(m.getCategory());
		f.setPrice(java.math.BigDecimal.valueOf(m.getPrice()).setScale(2, java.math.RoundingMode.HALF_UP));
		f.setSlotNumber(m.getSlotNumber());
		f.setExpiryDate(m.getExpiryDate());
		f.setPrescriptionRequired(m.isPrescriptionRequired());
		return f;
	}

	private static void addExtraErrors(BindingResult result, Map<String, String> extra) {
		extra.forEach((field, message) -> {
			if (!result.hasFieldErrors(field)) {
				result.rejectValue(field, "invalid", message);
			}
		});
	}

	/** Null (not an exception) for missing/non-numeric input; the service then rejects it with a message. */
	private static Integer parseInt(String value) {
		if (value == null) {
			return null;
		}
		try {
			return Integer.valueOf(value.trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static String name(Principal principal) {
		return principal == null ? "" : principal.getName();
	}
}
