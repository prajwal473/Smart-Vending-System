package com.medicinevending.web.service;

import com.medicinevending.web.model.SaleTransaction;
import com.medicinevending.web.repository.MedicineRepository;
import com.medicinevending.web.repository.SaleTransactionRepository;
import com.medicinevending.web.repository.SaleTransactionSearchRepository;
import com.medicinevending.web.repository.SaleTransactionSearchRepository.Criteria;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stage 5D: read-only search, filtering, paging, summary and detail of recorded sales. */
@Service
public class TransactionHistoryService {

	public static final List<Integer> PAGE_SIZES = List.of(10, 25, 50, 100);
	public static final int DEFAULT_PAGE_SIZE = 25;
	static final int MAX_QUERY_LENGTH = 100;

	/** "42", "000042" or a receipt reference such as "TXN-00000042" (at most 9 digits so it fits an INT). */
	private static final Pattern ID_QUERY = Pattern.compile("(?i)^(?:txn-)?0*(\\d{1,9})$");

	private final SaleTransactionSearchRepository searchRepository;
	private final SaleTransactionRepository transactionRepository;
	private final MedicineRepository medicineRepository;

	public TransactionHistoryService(
			SaleTransactionSearchRepository searchRepository,
			SaleTransactionRepository transactionRepository,
			MedicineRepository medicineRepository) {
		this.searchRepository = searchRepository;
		this.transactionRepository = transactionRepository;
		this.medicineRepository = medicineRepository;
	}

	/**
	 * All inputs are the raw request strings. Invalid dates are reported (and no rows are returned)
	 * instead of being ignored, so an admin is never shown unfiltered data by mistake.
	 */
	@Transactional(readOnly = true)
	public TransactionHistoryResult search(String query, String startText, String endText, String pageText, String sizeText) {
		int size = parseSize(sizeText);

		String q = query == null ? "" : query.trim();
		if (q.length() > MAX_QUERY_LENGTH) {
			return TransactionHistoryResult.failed("The search text is too long (at most " + MAX_QUERY_LENGTH + " characters).", size);
		}

		LocalDate start;
		LocalDate end;
		try {
			start = parseDate(startText);
		} catch (DateTimeParseException ex) {
			return TransactionHistoryResult.failed("The start date is not a valid date. Use the date picker (YYYY-MM-DD).", size);
		}
		try {
			end = parseDate(endText);
		} catch (DateTimeParseException ex) {
			return TransactionHistoryResult.failed("The end date is not a valid date. Use the date picker (YYYY-MM-DD).", size);
		}
		if (start != null && end != null && start.isAfter(end)) {
			return TransactionHistoryResult.failed("The start date must be on or before the end date.", size);
		}

		Integer idMatch = null;
		String namePattern = null;
		if (!q.isEmpty()) {
			Matcher m = ID_QUERY.matcher(q);
			if (m.matches()) {
				idMatch = Integer.valueOf(m.group(1));
			}
			namePattern = likePattern(q);
		}
		// The end date is INCLUSIVE of the whole day: everything before midnight at the start of the next day.
		LocalDateTime from = start == null ? null : start.atStartOfDay();
		LocalDateTime toExclusive = end == null ? null : end.plusDays(1).atStartOfDay();
		Criteria criteria = new Criteria(idMatch, namePattern, from, toExclusive);

		long total = searchRepository.count(criteria);
		int totalPages = (int) Math.max(1, (total + size - 1) / size);
		int page = Math.min(parsePage(pageText), totalPages);
		List<SaleTransaction> rows = total == 0
				? List.of()
				: searchRepository.find(criteria, (page - 1) * size, size);

		long first = rows.isEmpty() ? 0 : (long) (page - 1) * size + 1;
		long last = rows.isEmpty() ? 0 : first + rows.size() - 1;
		return new TransactionHistoryResult(
				rows,
				total,
				searchRepository.sumTotalAmount(criteria),
				page,
				size,
				total == 0 ? 0 : totalPages,
				first,
				last,
				window(page, total == 0 ? 0 : totalPages),
				null);
	}

	/** Returns the transaction, or empty for a non-numeric, out-of-range or unknown id. */
	@Transactional(readOnly = true)
	public Optional<SaleTransaction> findDetail(String idText) {
		if (idText == null || !idText.trim().matches("\\d{1,9}")) {
			return Optional.empty();
		}
		return transactionRepository.findById(Integer.valueOf(idText.trim()));
	}

	/** True when the sold medicine still exists in inventory (it may have been removed later). */
	@Transactional(readOnly = true)
	public boolean medicineStillExists(Integer medicineId) {
		return medicineId != null && medicineRepository.existsById(medicineId);
	}

	// ------------------------------------------------------------------ helpers

	/** Lower-cased "%text%" with LIKE wildcards in the text escaped, so "100%" only matches a literal "100%". */
	static String likePattern(String text) {
		char esc = SaleTransactionSearchRepository.LIKE_ESCAPE;
		String escaped = text.toLowerCase(Locale.ROOT)
				.replace(String.valueOf(esc), esc + "" + esc)
				.replace("%", esc + "%")
				.replace("_", esc + "_");
		return "%" + escaped + "%";
	}

	private static LocalDate parseDate(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		return LocalDate.parse(text.trim());
	}

	private static int parseSize(String text) {
		try {
			int size = Integer.parseInt(text == null ? "" : text.trim());
			return PAGE_SIZES.contains(size) ? size : DEFAULT_PAGE_SIZE;
		} catch (NumberFormatException ex) {
			return DEFAULT_PAGE_SIZE;
		}
	}

	private static int parsePage(String text) {
		try {
			return Math.max(1, Integer.parseInt(text == null ? "" : text.trim()));
		} catch (NumberFormatException ex) {
			return 1;
		}
	}

	private static List<Integer> window(int page, int totalPages) {
		List<Integer> pages = new ArrayList<>();
		for (int p = Math.max(1, page - 2); p <= Math.min(totalPages, page + 2); p++) {
			pages.add(p);
		}
		return pages;
	}
}
