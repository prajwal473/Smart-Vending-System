package com.medicinevending.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicinevending.web.service.PaymentSimulationService.Mode;
import com.medicinevending.web.service.PurchaseService;
import com.medicinevending.web.service.TransactionHistoryResult;
import com.medicinevending.web.service.TransactionHistoryService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Stage 5D. In-memory H2 (profile "test") only - never the real MySQL database.
 * Every row is inserted explicitly by the test; nothing is invented by the application.
 */
@SpringBootTest(properties = { "app.admin.username=", "app.admin.password=", "app.admin.password-hash=" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTransactionTests {

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired TransactionHistoryService history;
	@Autowired PurchaseService purchaseService;

	static final String LINK = "href=\"/admin/transactions/";

	@BeforeEach
	void clean() {
		jdbc.update("DELETE FROM transactions");
		jdbc.update("DELETE FROM medicines");
	}

	// ---------------------------------------------------------------- helpers

	private int tx(Integer medicineId, String name, int qty, String total, String timestamp) {
		jdbc.update("INSERT INTO transactions (medicine_id, medicine_name, quantity, total_amount, transaction_date)"
				+ " VALUES (?,?,?,?,?)", medicineId, name, qty, new BigDecimal(total), Timestamp.valueOf(timestamp));
		return jdbc.queryForObject("SELECT MAX(transaction_id) FROM transactions", Integer.class);
	}

	private int medicine(String name, String price, int stock) {
		jdbc.update("INSERT INTO medicines (medicine_name, category, price, stock, slot_number, expiry_date, prescription_required)"
				+ " VALUES (?,?,?,?,?,?,?)", name, "Test", new BigDecimal(price), stock, 1, LocalDate.now().plusYears(1), false);
		return jdbc.queryForObject("SELECT MAX(medicine_id) FROM medicines", Integer.class);
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder b) {
		return b.with(user("tester").roles("ADMIN"));
	}

	/** GET /admin/transactions as an admin with name/value query parameters; returns the HTML. */
	private String page(String... nameValue) throws Exception {
		MockHttpServletRequestBuilder b = asAdmin(get("/admin/transactions"));
		for (int i = 0; i < nameValue.length; i += 2) {
			b.param(nameValue[i], nameValue[i + 1]);
		}
		return mockMvc.perform(b).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static int occurrences(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
			n++;
		}
		return n;
	}

	// ---------------------------------------------------------------- security

	@Test
	void unauthenticatedUsersCannotSeeTransactions() throws Exception {
		int id = tx(1, "SecretSale", 1, "10.00", "2026-03-10 09:00:00");
		for (String url : new String[] { "/admin/transactions", "/admin/transactions?q=Secret",
				"/admin/transactions?start=2026-01-01&end=2026-12-31&page=2&size=10", "/admin/transactions/" + id,
				"/admin/transactions/99999" }) {
			mockMvc.perform(get(url))
					.andExpect(status().is3xxRedirection())
					.andExpect(redirectedUrl("/admin/login"))
					.andExpect(content().string(""));
		}
	}

	@Test
	void historyIsReadOnlyAndKeepsCsrfProtection() throws Exception {
		int id = tx(1, "Keep", 1, "10.00", "2026-03-10 09:00:00");
		// a logged-in admin cannot POST to the history (no such action), and CSRF is still enforced
		mockMvc.perform(asAdmin(post("/admin/transactions")).with(csrf())).andExpect(status().isMethodNotAllowed());
		mockMvc.perform(asAdmin(post("/admin/transactions"))).andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/transactions/" + id).with(csrf())).andExpect(redirectedUrl("/admin/login"));
		assertThat(count("transactions")).isEqualTo(1);
	}

	// ---------------------------------------------------------------- list

	@Test
	void showsTransactionsNewestFirstWithEveryField() throws Exception {
		int a = tx(7, "Alpha", 2, "51.00", "2026-03-10 09:00:00");
		tx(null, "Gamma", 1, "5.00", "2026-03-12 18:30:15");
		tx(8, "Beta", 3, "30.00", "2026-03-11 12:00:00");

		String html = page();
		assertThat(html.indexOf("Gamma")).isPositive().isLessThan(html.indexOf("Beta"));
		assertThat(html.indexOf("Beta")).isLessThan(html.indexOf("Alpha"));
		assertThat(html).contains("₹51.00", "12 Mar 2026, 18:30:15", "10 Mar 2026, 09:00:00")
				.contains(LINK + a + "\"");
		assertThat(occurrences(html, LINK)).isEqualTo(3);
	}

	@Test
	void emptyHistoryShowsAHelpfulMessage() throws Exception {
		String html = page();
		assertThat(html).contains("No transactions have been recorded yet", "₹0.00", "Page 1 of 1");
		assertThat(occurrences(html, LINK)).isZero();
	}

	@Test
	void emptyResultForFiltersSaysNothingMatched() throws Exception {
		tx(1, "Alpha", 1, "5.00", "2026-03-10 09:00:00");
		String html = page("q", "zzz-no-such-medicine");
		assertThat(html).contains("No transactions match these filters").doesNotContain("Alpha");
	}

	// ---------------------------------------------------------------- search

	@Test
	void searchByTransactionId() throws Exception {
		tx(1, "Alpha", 1, "5.00", "2026-03-10 09:00:00");
		int b = tx(1, "Beta", 1, "6.00", "2026-03-11 09:00:00");
		tx(1, "Gamma", 1, "7.00", "2026-03-12 09:00:00");

		for (String q : new String[] { String.valueOf(b), "TXN-" + String.format("%08d", b), "txn-" + b, "000" + b }) {
			String html = page("q", q);
			assertThat(html).as(q).contains("Beta").doesNotContain("Alpha").doesNotContain("Gamma");
		}
	}

	@Test
	void searchByMedicineNameIsCaseInsensitiveAndPartial() throws Exception {
		tx(1, "Alpha", 1, "5.00", "2026-03-10 09:00:00");
		tx(1, "Beta Syrup", 1, "6.00", "2026-03-11 09:00:00");
		tx(1, "Gamma", 1, "7.00", "2026-03-12 09:00:00");

		assertThat(page("q", "BETA")).contains("Beta Syrup").doesNotContain("Alpha").doesNotContain("Gamma");
		assertThat(page("q", "  yrup ")).contains("Beta Syrup").doesNotContain("Alpha");
	}

	@Test
	void searchInputIsTreatedAsDataNotAsSqlOrWildcards() throws Exception {
		tx(1, "100% Pure", 1, "5.00", "2026-03-10 09:00:00");
		tx(1, "100X Pure", 1, "6.00", "2026-03-11 09:00:00");
		tx(1, "A_B", 1, "7.00", "2026-03-12 09:00:00");
		tx(1, "AXB", 1, "8.00", "2026-03-13 09:00:00");

		assertThat(page("q", "100%")).contains("100% Pure").doesNotContain("100X Pure");
		assertThat(page("q", "%")).contains("100% Pure").doesNotContain("AXB").doesNotContain("A_B");
		assertThat(page("q", "a_b")).contains("A_B").doesNotContain("AXB");

		String injected = page("q", "' OR '1'='1");
		assertThat(injected).contains("No transactions match these filters").doesNotContain("100X Pure");
		page("q", "'; DROP TABLE transactions; --");
		assertThat(count("transactions")).isEqualTo(4);
	}

	// ---------------------------------------------------------------- date range

	private void seedDates() {
		tx(1, "Early", 1, "1.00", "2026-03-10 00:00:00");
		tx(1, "Late", 1, "2.00", "2026-03-10 23:59:59");
		tx(1, "Midnight", 1, "4.00", "2026-03-11 00:00:00");
		tx(1, "Later", 1, "8.00", "2026-03-12 12:00:00");
	}

	@Test
	void endDateIncludesTheWholeDay() throws Exception {
		seedDates();
		String html = page("start", "2026-03-10", "end", "2026-03-10");
		assertThat(html).contains("Early", "Late").doesNotContain("Midnight").doesNotContain("Later");
		TransactionHistoryResult r = history.search(null, "2026-03-10", "2026-03-10", null, null);
		assertThat(r.totalCount()).isEqualTo(2);
		assertThat(r.totalSales()).isEqualByComparingTo("3.00");
	}

	@Test
	void startOnlyEndOnlyAndFullRange() throws Exception {
		seedDates();
		assertThat(history.search(null, "2026-03-11", null, null, null).totalCount()).isEqualTo(2);
		assertThat(history.search(null, null, "2026-03-10", null, null).totalCount()).isEqualTo(2);
		assertThat(history.search(null, "2026-03-10", "2026-03-12", null, null).totalCount()).isEqualTo(4);
		assertThat(history.search(null, "", "", null, null).totalCount()).isEqualTo(4);
		assertThat(history.search(null, "2027-01-01", null, null, null).totalCount()).isZero();
	}

	@Test
	void dateFilterCombinesWithNameSearch() throws Exception {
		seedDates();
		TransactionHistoryResult r = history.search("late", "2026-03-10", "2026-03-11", null, null);
		assertThat(r.totalCount()).isEqualTo(1); // "Late" only; "Later" is on 12 March
		assertThat(r.rows().get(0).getMedicineName()).isEqualTo("Late");
	}

	@Test
	void invalidDatesAreReportedAndNeverReturnUnfilteredData() throws Exception {
		seedDates();
		String[][] bad = { { "2026-02-30", null }, { "abc", null }, { "10/03/2026", null }, { null, "2026-13-01" },
				{ null, "nope" }, { "2026-03-12", "2026-03-10" } };
		for (String[] d : bad) {
			TransactionHistoryResult r = history.search(null, d[0], d[1], null, null);
			assertThat(r.hasError()).as(d[0] + " / " + d[1]).isTrue();
			assertThat(r.rows()).isEmpty();
			assertThat(r.totalCount()).isZero();
		}
		assertThat(history.search("x".repeat(101), null, null, null, null).hasError()).isTrue();

		String html = page("start", "abc");
		assertThat(html).contains("not a valid date").doesNotContain("Early").doesNotContain("Later");
		html = page("start", "2026-03-12", "end", "2026-03-10");
		assertThat(html).contains("start date must be on or before the end date").doesNotContain("Early");
	}

	// ---------------------------------------------------------------- pagination & summary

	private void seedBulk() {
		for (int i = 1; i <= 30; i++) {
			tx(1, String.format("Bulk %02d", i), 1, "2.00", String.format("2026-04-01 10:%02d:00", i - 1));
		}
		for (int i = 1; i <= 5; i++) {
			tx(1, "Other " + i, 1, "5.00", String.format("2026-04-01 11:%02d:00", i));
		}
	}

	@Test
	void paginationWorksWithDefaultAndChosenPageSizes() throws Exception {
		seedBulk();

		String p1 = page();
		assertThat(occurrences(p1, LINK)).isEqualTo(25);
		assertThat(p1).contains("Page 1 of 2", "Showing 1–25 of 35");
		String p2 = page("page", "2");
		assertThat(occurrences(p2, LINK)).isEqualTo(10);
		assertThat(p2).contains("Page 2 of 2", "Showing 26–35 of 35");

		assertThat(page("size", "10")).contains("Page 1 of 4");
		String last = page("size", "10", "page", "99"); // clamped to the last page
		assertThat(last).contains("Page 4 of 4");
		assertThat(occurrences(last, LINK)).isEqualTo(5);
		assertThat(page("page", "abc")).contains("Page 1 of 2");
		assertThat(page("page", "-3")).contains("Page 1 of 2");
		assertThat(page("size", "7")).contains("Page 1 of 2"); // unsupported size falls back to 25
		assertThat(page("size", "100")).contains("Page 1 of 1");
	}

	@Test
	void paginationAndSummaryStayConsistentWithFilters() throws Exception {
		seedBulk();

		String p1 = page("q", "bulk", "size", "10");
		assertThat(p1).contains("Page 1 of 3", "Bulk 30", "₹60.00").doesNotContain("Other").doesNotContain("Bulk 01");
		assertThat(p1).contains("q=bulk"); // paging links keep the filter
		String p3 = page("q", "bulk", "size", "10", "page", "3");
		assertThat(p3).contains("Bulk 01", "Page 3 of 3", "₹60.00").doesNotContain("Bulk 30");
		assertThat(occurrences(p3, LINK)).isEqualTo(10);

		// the summary covers ALL matches, not just the visible page
		TransactionHistoryResult second = history.search("bulk", "2026-04-01", "2026-04-01", "2", "10");
		assertThat(second.totalCount()).isEqualTo(30);
		assertThat(second.totalSales()).isEqualByComparingTo("60.00");
		assertThat(second.rows()).hasSize(10);
	}

	@Test
	void summaryMatchesTheDatabaseAndCountsEachTransactionOnce() throws Exception {
		seedBulk();
		TransactionHistoryResult all = history.search(null, null, null, null, null);
		assertThat(all.totalCount()).isEqualTo(count("transactions"));
		assertThat(all.totalSales()).isEqualByComparingTo(jdbc.queryForObject("SELECT SUM(total_amount) FROM transactions", BigDecimal.class));
		assertThat(all.totalSales()).isEqualByComparingTo("85.00");

		TransactionHistoryResult others = history.search("other", null, null, null, null);
		assertThat(others.totalCount()).isEqualTo(5);
		assertThat(others.totalSales()).isEqualByComparingTo("25.00");

		TransactionHistoryResult none = history.search("nothing-matches", null, null, null, null);
		assertThat(none.totalCount()).isZero();
		assertThat(none.totalSales()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void viewingHistoryNeverChangesData() throws Exception {
		seedBulk();
		int before = count("transactions");
		page("q", "bulk", "start", "2026-04-01");
		assertThat(count("transactions")).isEqualTo(before);
	}

	// ---------------------------------------------------------------- details

	@Test
	void transactionDetailsShowOnlyRealFields() throws Exception {
		int med = medicine("Paracetamol", "25.50", 10);
		int id = tx(med, "Paracetamol", 2, "51.00", "2026-03-10 14:05:09");

		mockMvc.perform(asAdmin(get("/admin/transactions/" + id)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("TXN-" + String.format("%08d", id))))
				.andExpect(content().string(containsString("Paracetamol")))
				.andExpect(content().string(containsString("₹51.00")))
				.andExpect(content().string(containsString("10 Mar 2026, 14:05:09")))
				.andExpect(content().string(containsString("open its record")))
				.andExpect(content().string(containsString("href=\"/admin/medicines\"")))
				.andExpect(content().string(containsString("href=\"/admin/dashboard\"")));
	}

	@Test
	void detailsHandleASaleWhoseMedicineIsGoneOrMissing() throws Exception {
		int gone = tx(99999, "Removed Med", 1, "5.00", "2026-03-10 09:00:00");
		int none = tx(null, "No Id Med", 1, "5.00", "2026-03-10 10:00:00");
		for (int id : new int[] { gone, none }) {
			mockMvc.perform(asAdmin(get("/admin/transactions/" + id)))
					.andExpect(status().isOk())
					.andExpect(content().string(containsString("no longer in the inventory")));
		}
	}

	@Test
	void nonexistentTransactionIdsGiveAClearNotFoundPage() throws Exception {
		tx(1, "Alpha", 1, "5.00", "2026-03-10 09:00:00");
		for (String id : new String[] { "99999", "abc", "-1", "99999999999", "0" }) {
			mockMvc.perform(asAdmin(get("/admin/transactions/" + id)))
					.andExpect(status().isNotFound())
					.andExpect(content().string(containsString("Transaction not found")));
		}
	}

	// ---------------------------------------------------------------- navigation & existing behaviour

	@Test
	void dashboardAndSidebarLinkToTransactionHistory() throws Exception {
		int id = tx(1, "Alpha", 1, "5.00", "2026-03-10 09:00:00");
		mockMvc.perform(asAdmin(get("/admin/dashboard")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("href=\"/admin/transactions\"")))
				.andExpect(content().string(containsString(LINK + id + "\"")))
				.andExpect(content().string(containsString("View all")));
		mockMvc.perform(asAdmin(get("/admin/medicines")))
				.andExpect(content().string(containsString("href=\"/admin/transactions\"")));
		assertThat(page()).contains("href=\"/admin/dashboard\"", "href=\"/admin/medicines\"");
	}

	@Test
	void aRealPurchaseAppearsInTheHistory() throws Exception {
		int med = medicine("Cetirizine", "25.50", 10);
		purchaseService.completePurchase(med, 2, Mode.SUCCESS);

		assertThat(count("transactions")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT stock FROM medicines WHERE medicine_id=?", Integer.class, med)).isEqualTo(8);
		String html = page();
		assertThat(html).contains("Cetirizine", "₹51.00").contains(LINK);
	}
}
