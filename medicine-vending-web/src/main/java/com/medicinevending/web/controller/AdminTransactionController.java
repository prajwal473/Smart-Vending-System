package com.medicinevending.web.controller;

import com.medicinevending.web.model.SaleTransaction;
import com.medicinevending.web.service.TransactionHistoryResult;
import com.medicinevending.web.service.TransactionHistoryService;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Stage 5D: read-only transaction history. Everything is GET (nothing here changes data), and every route is
 * under /admin/** so SecurityConfig already requires the ADMIN role.
 */
@Controller
@RequestMapping("/admin/transactions")
public class AdminTransactionController {

	private final TransactionHistoryService service;

	public AdminTransactionController(TransactionHistoryService service) {
		this.service = service;
	}

	@GetMapping
	public String history(
			@RequestParam(name = "q", required = false) String q,
			@RequestParam(name = "start", required = false) String start,
			@RequestParam(name = "end", required = false) String end,
			@RequestParam(name = "page", required = false) String page,
			@RequestParam(name = "size", required = false) String size,
			Principal principal,
			Model model) {

		TransactionHistoryResult result = service.search(q, start, end, page, size);
		model.addAttribute("adminName", principal == null ? "" : principal.getName());
		model.addAttribute("h", result);
		model.addAttribute("q", q == null ? "" : q.trim());
		model.addAttribute("start", start == null ? "" : start.trim());
		model.addAttribute("end", end == null ? "" : end.trim());
		model.addAttribute("filtered", !(blank(q) && blank(start) && blank(end)));
		model.addAttribute("sizeOptions", TransactionHistoryService.PAGE_SIZES);
		return "admin/transactions";
	}

	@GetMapping("/{id}")
	public String detail(
			@PathVariable("id") String id,
			Principal principal,
			HttpServletResponse response,
			Model model) {

		model.addAttribute("adminName", principal == null ? "" : principal.getName());
		Optional<SaleTransaction> transaction = service.findDetail(id);
		if (transaction.isEmpty()) {
			response.setStatus(HttpStatus.NOT_FOUND.value());
			return "admin/transaction-detail";
		}
		model.addAttribute("transaction", transaction.get());
		model.addAttribute("medicineExists", service.medicineStillExists(transaction.get().getMedicineId()));
		return "admin/transaction-detail";
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
