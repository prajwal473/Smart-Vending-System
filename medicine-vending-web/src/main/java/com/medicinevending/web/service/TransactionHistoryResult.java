package com.medicinevending.web.service;

import com.medicinevending.web.model.SaleTransaction;
import java.math.BigDecimal;
import java.util.List;

/** One page of the admin transaction history plus the summary for ALL rows matching the filters. */
public record TransactionHistoryResult(
		List<SaleTransaction> rows,
		long totalCount,
		BigDecimal totalSales,
		int page,
		int size,
		int totalPages,
		long firstShown,
		long lastShown,
		List<Integer> pageWindow,
		String error) {

	public boolean hasError() {
		return error != null;
	}

	public boolean hasPrevious() {
		return page > 1;
	}

	public boolean hasNext() {
		return page < totalPages;
	}

	static TransactionHistoryResult failed(String error, int size) {
		return new TransactionHistoryResult(List.of(), 0L, BigDecimal.ZERO, 1, size, 0, 0L, 0L, List.of(), error);
	}
}
