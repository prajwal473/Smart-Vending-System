package com.medicinevending.web.controller;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-browser-session bookkeeping for the purchase workflow (kept in the HTTP session, not the database,
 * so no schema change is needed).
 *
 * Every confirmation page gets a ONE-TIME token. The token remembers which medicine and quantity were
 * confirmed. Submitting the same token twice (double click, refresh, back button) can never create a
 * second purchase: the first submit "claims" the token, later ones are told it is in progress or done.
 */
public class PurchaseSessionState implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final int MAX_OPEN_TOKENS = 20;

	public record Ticket(int medicineId, int quantity) implements Serializable {
	}

	public enum ClaimStatus {
		CLAIMED, IN_PROGRESS, ALREADY_COMPLETED, UNKNOWN
	}

	public record Claim(ClaimStatus status, Ticket ticket, Integer transactionId) {
	}

	private final LinkedHashMap<String, Ticket> open = new LinkedHashMap<>();
	private final Map<String, Ticket> inFlight = new HashMap<>();
	private final Map<String, Integer> completed = new HashMap<>();
	private final Set<Integer> ownedReceipts = new HashSet<>();

	public synchronized String issue(int medicineId, int quantity) {
		if (open.size() >= MAX_OPEN_TOKENS) {
			Iterator<String> oldest = open.keySet().iterator();
			oldest.next();
			oldest.remove();
		}
		String token = UUID.randomUUID().toString();
		open.put(token, new Ticket(medicineId, quantity));
		return token;
	}

	/** Atomically consumes a token. Only the first caller gets CLAIMED. */
	public synchronized Claim claim(String token) {
		if (token == null) {
			return new Claim(ClaimStatus.UNKNOWN, null, null);
		}
		Ticket ticket = open.remove(token);
		if (ticket != null) {
			inFlight.put(token, ticket);
			return new Claim(ClaimStatus.CLAIMED, ticket, null);
		}
		if (inFlight.containsKey(token)) {
			return new Claim(ClaimStatus.IN_PROGRESS, null, null);
		}
		Integer transactionId = completed.get(token);
		if (transactionId != null) {
			return new Claim(ClaimStatus.ALREADY_COMPLETED, null, transactionId);
		}
		return new Claim(ClaimStatus.UNKNOWN, null, null);
	}

	public synchronized void complete(String token, int transactionId) {
		inFlight.remove(token);
		completed.put(token, transactionId);
		ownedReceipts.add(transactionId);
	}

	/** Attempt ended without a purchase (rejected or payment failed). The token stays used up. */
	public synchronized void release(String token) {
		inFlight.remove(token);
	}

	public synchronized boolean ownsReceipt(int transactionId) {
		return ownedReceipts.contains(transactionId);
	}
}
