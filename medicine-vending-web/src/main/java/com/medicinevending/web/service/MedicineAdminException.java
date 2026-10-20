package com.medicinevending.web.service;

/** Raised by {@link MedicineAdminService} for expected, user-explainable problems (message is safe to show). */
public class MedicineAdminException extends RuntimeException {

	public enum Reason { NOT_FOUND, INVALID, DELETE_BLOCKED, STOCK_CONFLICT }

	private final Reason reason;

	public MedicineAdminException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}
}
