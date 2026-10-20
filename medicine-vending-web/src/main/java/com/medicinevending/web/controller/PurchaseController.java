package com.medicinevending.web.controller;

import com.medicinevending.web.controller.PurchaseSessionState.Claim;
import com.medicinevending.web.model.SaleTransaction;
import com.medicinevending.web.service.PaymentSimulationService;
import com.medicinevending.web.service.PurchaseQuote;
import com.medicinevending.web.service.PurchaseRejectedException;
import com.medicinevending.web.service.PurchaseService;
import com.medicinevending.web.service.PurchaseService.PurchaseOutcome;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.WebUtils;

@Controller
@RequestMapping("/purchase")
public class PurchaseController {

	private static final String SESSION_KEY = "purchaseSessionState";

	private final PurchaseService purchaseService;

	public PurchaseController(PurchaseService purchaseService) {
		this.purchaseService = purchaseService;
	}

	/** Step 1: purchase confirmation page. The browser sends only a medicine id and quantity. */
	@GetMapping("/confirm")
	public String confirm(
			@RequestParam(name = "medicineId", required = false) String medicineId,
			@RequestParam(name = "quantity", required = false) String quantity,
			HttpSession session,
			HttpServletResponse response,
			Model model) {

		try {
			PurchaseQuote quote = purchaseService.prepareQuote(parseInt(medicineId), parseInt(quantity));
			String token = stateFor(session).issue(quote.getMedicineId(), quote.getQuantity());
			model.addAttribute("quote", quote);
			model.addAttribute("token", token);
			return "purchase-confirm";
		} catch (PurchaseRejectedException ex) {
			return rejected(ex, response, model);
		}
	}

	/** Step 2: simulated payment. Price, stock, expiry and prescription rules are re-checked on the server. */
	@PostMapping("/pay")
	public String pay(
			@RequestParam(name = "token", required = false) String token,
			@RequestParam(name = "outcome", required = false) String outcome,
			HttpSession session,
			HttpServletResponse response,
			Model model) {

		PaymentSimulationService.Mode mode = PaymentSimulationService.Mode.fromParameter(outcome);
		if (mode == null) {
			return message(response, model, HttpStatus.BAD_REQUEST, "Invalid request",
					"The payment option was not recognised. Please start again.", null);
		}

		PurchaseSessionState state = stateFor(session);
		Claim claim = state.claim(token);

		switch (claim.status()) {
			case ALREADY_COMPLETED:
				// Duplicate submission: show the receipt that already exists. Nothing is charged again.
				return "redirect:/purchase/receipt/" + claim.transactionId();
			case IN_PROGRESS:
				return message(response, model, HttpStatus.CONFLICT, "Payment already in progress",
						"This payment is already being processed. Please wait a moment and do not submit again.", null);
			case UNKNOWN:
				return message(response, model, HttpStatus.CONFLICT, "This purchase form is no longer valid",
						"It may have expired or already been used. Please start the purchase again.", null);
			default:
				break;
		}

		PurchaseSessionState.Ticket ticket = claim.ticket();
		try {
			PurchaseOutcome result = purchaseService.completePurchase(ticket.medicineId(), ticket.quantity(), mode);

			if (!result.paymentSucceeded()) {
				state.release(token);
				String retry = "/purchase/confirm?medicineId=" + ticket.medicineId() + "&quantity=" + ticket.quantity();
				return message(response, model, HttpStatus.PAYMENT_REQUIRED, "Payment failed",
						result.paymentMessage() + " No money was taken and the stock was not changed.", retry);
			}

			state.complete(token, result.transactionId());
			return "redirect:/purchase/receipt/" + result.transactionId();
		} catch (PurchaseRejectedException ex) {
			state.release(token);
			return rejected(ex, response, model);
		} catch (RuntimeException ex) {
			state.release(token);
			return message(response, model, HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong",
					"The purchase could not be completed and nothing was charged. Please try again.", null);
		}
	}

	/** Step 3: receipt. Only visible in the browser session that made the purchase. */
	@GetMapping("/receipt/{transactionId}")
	public String receipt(
			@PathVariable("transactionId") Integer transactionId,
			HttpSession session,
			HttpServletResponse response,
			Model model) {

		if (transactionId == null || !stateFor(session).ownsReceipt(transactionId)) {
			return message(response, model, HttpStatus.NOT_FOUND, "Receipt not found",
					"This receipt is not available in your current session.", null);
		}
		SaleTransaction transaction = purchaseService.findReceipt(transactionId).orElse(null);
		if (transaction == null) {
			return message(response, model, HttpStatus.NOT_FOUND, "Receipt not found",
					"This receipt could not be found.", null);
		}
		model.addAttribute("transaction", transaction);
		return "purchase-receipt";
	}

	// ------------------------------------------------------------------ helpers

	private PurchaseSessionState stateFor(HttpSession session) {
		synchronized (WebUtils.getSessionMutex(session)) {
			PurchaseSessionState state = (PurchaseSessionState) session.getAttribute(SESSION_KEY);
			if (state == null) {
				state = new PurchaseSessionState();
				session.setAttribute(SESSION_KEY, state);
			}
			return state;
		}
	}

	/** Returns null (not an exception) for missing or non-numeric input; the service then rejects it. */
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

	private String rejected(PurchaseRejectedException ex, HttpServletResponse response, Model model) {
		HttpStatus status = switch (ex.getReason()) {
			case MEDICINE_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case PRESCRIPTION_REQUIRED -> HttpStatus.FORBIDDEN;
			case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
			default -> HttpStatus.CONFLICT;
		};
		String title = ex.getReason() == PurchaseRejectedException.Reason.PRESCRIPTION_REQUIRED
				? "Prescription required"
				: "This purchase cannot be completed";
		return message(response, model, status, title, ex.getMessage(), null);
	}

	private String message(HttpServletResponse response, Model model, HttpStatus status,
			String title, String text, String retryUrl) {
		response.setStatus(status.value());
		model.addAttribute("title", title);
		model.addAttribute("text", text);
		model.addAttribute("retryUrl", retryUrl);
		return "purchase-message";
	}
}
