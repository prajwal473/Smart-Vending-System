package com.medicinevending.web.service;

/** Thrown when a purchase request fails validation. The message is safe to show to the customer. */
public class PurchaseRejectedException extends RuntimeException {

	public enum Reason {
		INVALID_REQUEST,
		MEDICINE_NOT_FOUND,
		PRESCRIPTION_REQUIRED,
		EXPIRED,
		OUT_OF_STOCK,
		INSUFFICIENT_STOCK,
		PRICE_UNAVAILABLE
	}

	private final Reason reason;

	public PurchaseRejectedException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}
}
