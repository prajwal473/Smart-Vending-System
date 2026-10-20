package com.medicinevending.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicinevending.web.service.MedicineAdminException;
import com.medicinevending.web.service.MedicineAdminService;
import com.medicinevending.web.service.PaymentSimulationService.Mode;
import com.medicinevending.web.service.PurchaseRejectedException;
import com.medicinevending.web.service.PurchaseService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * Stage 5C. In-memory H2 (profile "test") only - never the real MySQL database.
 * Logged-in admin is simulated with Spring Security's test support; real login is covered by AdminAuthTests.
 */
@SpringBootTest(properties = { "app.admin.username=", "app.admin.password=", "app.admin.password-hash=" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminMedicineTests {

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired MedicineAdminService adminService;
	@Autowired PurchaseService purchaseService;

	static final String FUTURE = LocalDate.now().plusYears(1).toString();

	@BeforeEach
	void clean() {
		jdbc.update("DELETE FROM transactions");
		jdbc.update("DELETE FROM medicines");
	}

	// ---------------------------------------------------------------- helpers

	private int insert(String name, String price, int stock, int slot, LocalDate expiry, boolean rx) {
		jdbc.update("INSERT INTO medicines (medicine_name, category, price, stock, slot_number, expiry_date, prescription_required)"
				+ " VALUES (?,?,?,?,?,?,?)", name, "Pain", new BigDecimal(price), stock, slot, expiry, rx);
		return jdbc.queryForObject("SELECT MAX(medicine_id) FROM medicines", Integer.class);
	}

	private int stock(int id) {
		return jdbc.queryForObject("SELECT stock FROM medicines WHERE medicine_id=?", Integer.class, id);
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private Map<String, Object> row(int id) {
		return jdbc.queryForMap("SELECT medicine_name, category, price, stock, slot_number, expiry_date, prescription_required"
				+ " FROM medicines WHERE medicine_id=?", id);
	}

	private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder b) {
		return b.with(user("tester").roles("ADMIN")).with(csrf());
	}

	private MockHttpServletRequestBuilder formPost(String url, String name, String category, String price,
			String stock, String slot, String expiry, boolean rx) {
		MockHttpServletRequestBuilder b = post(url)
				.param("medicineName", name).param("category", category).param("price", price)
				.param("slotNumber", slot).param("expiryDate", expiry);
		if (stock != null) {
			b.param("stock", stock);
		}
		if (rx) {
			b.param("prescriptionRequired", "true");
		}
		return admin(b);
	}

	// ---------------------------------------------------------------- access control

	@Test
	void everyMedicineManagementRouteRequiresLogin() throws Exception {
		int id = insert("Paracetamol", "25.50", 10, 1, LocalDate.now().plusYears(1), false);
		String base = "/admin/medicines";
		for (String url : new String[] { base, base + "/new", base + "/" + id + "/edit", base + "/" + id + "/stock" }) {
			mockMvc.perform(get(url)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/login"));
		}
		// state-changing POSTs, even WITH a valid CSRF token, do nothing without a logged-in admin
		for (String url : new String[] { base + "/new", base + "/" + id + "/edit", base + "/" + id + "/delete",
				base + "/" + id + "/stock/adjust", base + "/" + id + "/stock/set" }) {
			mockMvc.perform(post(url).with(csrf()).param("medicineName", "Hacked").param("delta", "5").param("stock", "999"))
					.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/login"));
		}
		assertThat(count("medicines")).isEqualTo(1);
		assertThat(stock(id)).isEqualTo(10);
		assertThat(row(id).get("medicine_name")).isEqualTo("Paracetamol");
	}

	@Test
	void adminPostsWithoutCsrfTokenAreRejected() throws Exception {
		int id = insert("Paracetamol", "25.50", 10, 1, LocalDate.now().plusYears(1), false);
		for (String url : new String[] { "/admin/medicines/new", "/admin/medicines/" + id + "/edit",
				"/admin/medicines/" + id + "/delete", "/admin/medicines/" + id + "/stock/adjust",
				"/admin/medicines/" + id + "/stock/set" }) {
			mockMvc.perform(post(url).with(user("tester").roles("ADMIN")).param("delta", "5").param("stock", "99"))
					.andExpect(status().isForbidden());
		}
		assertThat(count("medicines")).isEqualTo(1);
		assertThat(stock(id)).isEqualTo(10);
	}

	@Test
	void getRequestsCannotChangeData() throws Exception {
		int id = insert("Paracetamol", "25.50", 10, 1, LocalDate.now().plusYears(1), false);
		mockMvc.perform(admin(get("/admin/medicines/" + id + "/delete")));
		mockMvc.perform(admin(get("/admin/medicines/" + id + "/stock/adjust").param("delta", "5")));
		assertThat(count("medicines")).isEqualTo(1);
		assertThat(stock(id)).isEqualTo(10);
	}

	// ---------------------------------------------------------------- inventory page & dashboard link

	@Test
	void inventoryPageShowsEveryColumn() throws Exception {
		insert("Paracetamol", "25.50", 10, 7, LocalDate.of(2031, 3, 15), false);
		insert("Strong Antibiotic", "80.00", 0, 8, LocalDate.now().minusDays(2), true);

		mockMvc.perform(admin(get("/admin/medicines")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Paracetamol")))
				.andExpect(content().string(containsString("Pain")))
				.andExpect(content().string(containsString("₹25.50")))
				.andExpect(content().string(containsString("15 Mar 2031")))
				.andExpect(content().string(containsString("Strong Antibiotic")))
				.andExpect(content().string(containsString("Required")))
				.andExpect(content().string(containsString("Expired")))
				.andExpect(content().string(containsString("Add medicine")))
				.andExpect(content().string(containsString("/admin/medicines/new")));
	}

	@Test
	void inventoryPageHandlesAnEmptyTable() throws Exception {
		mockMvc.perform(admin(get("/admin/medicines")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("No medicines yet")));
	}

	@Test
	void dashboardLinksToMedicineManagement() throws Exception {
		mockMvc.perform(admin(get("/admin/dashboard")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("href=\"/admin/medicines\"")));
	}

	// ---------------------------------------------------------------- add

	@Test
	void addFormIsShown() throws Exception {
		mockMvc.perform(admin(get("/admin/medicines/new")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Starting stock")))
				.andExpect(content().string(containsString("name=\"_csrf\"")));
	}

	@Test
	void adminCanAddAMedicine() throws Exception {
		mockMvc.perform(formPost("/admin/medicines/new", "  Ibuprofen 200  ", "Pain Relief", "12.50", "7", "3", FUTURE, true))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/medicines"))
				.andExpect(flash().attribute("flashSuccess", containsString("Ibuprofen 200")));

		assertThat(count("medicines")).isEqualTo(1);
		Map<String, Object> r = jdbc.queryForMap("SELECT * FROM medicines");
		assertThat(r.get("MEDICINE_NAME")).isEqualTo("Ibuprofen 200");
		assertThat(r.get("CATEGORY")).isEqualTo("Pain Relief");
		assertThat((BigDecimal) r.get("PRICE")).isEqualByComparingTo("12.50");
		assertThat(r.get("STOCK")).isEqualTo(7);
		assertThat(r.get("SLOT_NUMBER")).isEqualTo(3);
		assertThat(r.get("PRESCRIPTION_REQUIRED")).isEqualTo(true);
	}

	@Test
	void invalidMedicineDataIsRejectedAndNothingIsSaved() throws Exception {
		String longName = "x".repeat(101);
		String[][] cases = {
				// name, category, price, stock, slot, expiry, message that must be shown
				{ "", "Cat", "10.00", "5", "1", FUTURE, "Enter the medicine name" },
				{ "   ", "Cat", "10.00", "5", "1", FUTURE, "Enter the medicine name" },
				{ longName, "Cat", "10.00", "5", "1", FUTURE, "at most 100" },
				{ "N", "", "10.00", "5", "1", FUTURE, "Enter a category" },
				{ "N", "Cat", "0", "5", "1", FUTURE, "greater than zero" },
				{ "N", "Cat", "-5", "5", "1", FUTURE, "greater than zero" },
				{ "N", "Cat", "abc", "5", "1", FUTURE, "valid price" },
				{ "N", "Cat", "", "5", "1", FUTURE, "Enter the price" },
				{ "N", "Cat", "1.234", "5", "1", FUTURE, "2 decimal" },
				{ "N", "Cat", "100000000", "5", "1", FUTURE, "too large" },
				{ "N", "Cat", "10.00", "-1", "1", FUTURE, "cannot be negative" },
				{ "N", "Cat", "10.00", "abc", "1", FUTURE, "whole number" },
				{ "N", "Cat", "10.00", "", "1", FUTURE, "Enter the starting stock" },
				{ "N", "Cat", "10.00", "1000001", "1", FUTURE, "1,000,000" },
				{ "N", "Cat", "10.00", "5", "0", FUTURE, "1 or more" },
				{ "N", "Cat", "10.00", "5", "-3", FUTURE, "1 or more" },
				{ "N", "Cat", "10.00", "5", "abc", FUTURE, "whole number" },
				{ "N", "Cat", "10.00", "5", "", FUTURE, "Enter the slot number" },
				{ "N", "Cat", "10.00", "5", "10000", FUTURE, "9999 or less" },
				{ "N", "Cat", "10.00", "5", "1", "", "Choose the expiry date" },
				{ "N", "Cat", "10.00", "5", "1", "not-a-date", "valid expiry date" },
				{ "N", "Cat", "10.00", "5", "1", "1999-12-31", "realistic expiry" },
				{ "N", "Cat", "10.00", "5", "1", LocalDate.now().plusYears(30).toString(), "realistic expiry" },
		};
		for (String[] c : cases) {
			mockMvc.perform(formPost("/admin/medicines/new", c[0], c[1], c[2], c[3], c[4], c[5], false))
					.andExpect(status().isOk())
					.andExpect(content().string(containsString(c[6])));
		}
		assertThat(count("medicines")).isZero();
	}

	@Test
	void duplicateSlotIsRejectedWhenAdding() throws Exception {
		insert("Existing", "5.00", 1, 4, LocalDate.now().plusYears(1), false);
		mockMvc.perform(formPost("/admin/medicines/new", "Another", "Cat", "5.00", "1", "4", FUTURE, false))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Slot 4 is already used")));
		assertThat(count("medicines")).isEqualTo(1);
	}

	// ---------------------------------------------------------------- edit

	@Test
	void editFormShowsCurrentValues() throws Exception {
		int id = insert("Paracetamol", "25.50", 10, 7, LocalDate.of(2031, 3, 15), true);
		mockMvc.perform(admin(get("/admin/medicines/" + id + "/edit")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("value=\"Paracetamol\"")))
				.andExpect(content().string(containsString("value=\"25.50\"")))
				.andExpect(content().string(containsString("value=\"2031-03-15\"")))
				.andExpect(content().string(containsString("checked")));
	}

	@Test
	void adminCanEditDetailsButEditNeverChangesStock() throws Exception {
		int id = insert("Paracetamol", "25.50", 10, 1, LocalDate.now().plusYears(1), false);
		insert("Other", "5.00", 3, 2, LocalDate.now().plusYears(1), false);
		jdbc.update("INSERT INTO transactions (medicine_id, medicine_name, quantity, total_amount) VALUES (?,?,?,?)",
				id, "Paracetamol", 1, new BigDecimal("25.50"));

		// the "stock=999" sent here must be ignored; slot 1 is unchanged so it is not a duplicate
		mockMvc.perform(formPost("/admin/medicines/" + id + "/edit", "Paracetamol 650", "Fever", "30.00", "999", "1", FUTURE, true))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/medicines"))
				.andExpect(flash().attribute("flashSuccess", containsString("Paracetamol 650")));

		Map<String, Object> r = row(id);
		assertThat(r.get("MEDICINE_NAME")).isEqualTo("Paracetamol 650");
		assertThat(r.get("CATEGORY")).isEqualTo("Fever");
		assertThat((BigDecimal) r.get("PRICE")).isEqualByComparingTo("30.00");
		assertThat(r.get("PRESCRIPTION_REQUIRED")).isEqualTo(true);
		assertThat(r.get("STOCK")).isEqualTo(10);
		assertThat(count("transactions")).isEqualTo(1);
	}

	@Test
	void invalidEditIsRejectedAndNothingChanges() throws Exception {
		int id = insert("Paracetamol", "25.50", 10, 1, LocalDate.now().plusYears(1), false);
		Map<String, Object> before = row(id);
		mockMvc.perform(formPost("/admin/medicines/" + id + "/edit", "", "Pain", "-1", null, "0", "", false))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Enter the medicine name")))
				.andExpect(content().string(containsString("greater than zero")));
		assertThat(row(id)).isEqualTo(before);
	}

	@Test
	void changingToASlotUsedByAnotherMedicineIsRejected() throws Exception {
		int id = insert("A", "5.00", 1, 1, LocalDate.now().plusYears(1), false);
		insert("B", "5.00", 1, 2, LocalDate.now().plusYears(1), false);
		mockMvc.perform(formPost("/admin/medicines/" + id + "/edit", "A", "Pain", "5.00", null, "2", FUTURE, false))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Slot 2 is already used")));
		assertThat(row(id).get("SLOT_NUMBER")).isEqualTo(1);
	}

	@Test
	void nonexistentMedicineIdsAreHandledEverywhere() throws Exception {
		String missing = "Medicine #99999 could not be found";
		mockMvc.perform(admin(get("/admin/medicines/99999/edit")))
				.andExpect(redirectedUrl("/admin/medicines")).andExpect(flash().attribute("flashError", containsString(missing)));
		mockMvc.perform(admin(get("/admin/medicines/99999/stock")))
				.andExpect(redirectedUrl("/admin/medicines")).andExpect(flash().attribute("flashError", containsString(missing)));
		mockMvc.perform(formPost("/admin/medicines/99999/edit", "X", "Y", "1.00", null, "1", FUTURE, false))
				.andExpect(redirectedUrl("/admin/medicines")).andExpect(flash().attribute("flashError", containsString(missing)));
		mockMvc.perform(admin(post("/admin/medicines/99999/delete")))
				.andExpect(redirectedUrl("/admin/medicines")).andExpect(flash().attribute("flashError", containsString(missing)));
		mockMvc.perform(admin(post("/admin/medicines/99999/stock/adjust").param("delta", "5")))
				.andExpect(redirectedUrl("/admin/medicines")).andExpect(flash().attribute("flashError", containsString(missing)));
		mockMvc.perform(admin(post("/admin/medicines/99999/stock/set").param("stock", "5")))
				.andExpect(redirectedUrl("/admin/medicines")).andExpect(flash().attribute("flashError", containsString(missing)));
		assertThat(count("medicines")).isZero();
	}

	// ---------------------------------------------------------------- delete

	@Test
	void medicineWithoutSalesCanBeDeleted() throws Exception {
		int id = insert("Unsold", "5.00", 5, 1, LocalDate.now().plusYears(1), false);
		int keep = insert("Keep", "5.00", 5, 2, LocalDate.now().plusYears(1), false);
		mockMvc.perform(admin(post("/admin/medicines/" + id + "/delete")))
				.andExpect(redirectedUrl("/admin/medicines"))
				.andExpect(flash().attribute("flashSuccess", containsString("Deleted Unsold")));
		assertThat(count("medicines")).isEqualTo(1);
		assertThat(stock(keep)).isEqualTo(5);
	}

	@Test
	void medicineWithTransactionHistoryIsNeverDeleted() throws Exception {
		int id = insert("Sold", "25.50", 5, 1, LocalDate.now().plusYears(1), false);
		jdbc.update("INSERT INTO transactions (medicine_id, medicine_name, quantity, total_amount) VALUES (?,?,?,?)",
				id, "Sold", 2, new BigDecimal("51.00"));

		mockMvc.perform(admin(post("/admin/medicines/" + id + "/delete")))
				.andExpect(redirectedUrl("/admin/medicines"))
				.andExpect(flash().attribute("flashError", containsString("cannot be deleted")))
				.andExpect(flash().attribute("flashError", containsString("transaction")));

		assertThat(count("medicines")).isEqualTo(1);
		assertThat(count("transactions")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT total_amount FROM transactions", BigDecimal.class)).isEqualByComparingTo("51.00");
	}

	// ---------------------------------------------------------------- stock

	@Test
	void stockPageIsShown() throws Exception {
		int id = insert("Paracetamol", "25.50", 10, 1, LocalDate.now().plusYears(1), false);
		mockMvc.perform(admin(get("/admin/medicines/" + id + "/stock")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Add or remove units")))
				.andExpect(content().string(containsString("Set exact quantity")));
	}

	@Test
	void stockCanBeAdjustedAndSetWithoutTouchingOtherFields() throws Exception {
		int id = insert("Paracetamol", "25.50", 10, 7, LocalDate.of(2031, 3, 15), true);
		Map<String, Object> before = row(id);
		String back = "/admin/medicines/" + id + "/stock";

		mockMvc.perform(admin(post(back + "/adjust").param("delta", "5")))
				.andExpect(redirectedUrl(back)).andExpect(flash().attribute("flashSuccess", containsString("15")));
		assertThat(stock(id)).isEqualTo(15);

		mockMvc.perform(admin(post(back + "/adjust").param("delta", "-3")))
				.andExpect(redirectedUrl(back));
		assertThat(stock(id)).isEqualTo(12);

		mockMvc.perform(admin(post(back + "/set").param("stock", "0")))
				.andExpect(redirectedUrl(back)).andExpect(flash().attribute("flashSuccess", containsString("Stock set to 0")));
		assertThat(stock(id)).isZero();

		mockMvc.perform(admin(post(back + "/set").param("stock", "40")));
		Map<String, Object> after = row(id);
		assertThat(after.get("STOCK")).isEqualTo(40);
		for (String col : new String[] { "MEDICINE_NAME", "CATEGORY", "PRICE", "SLOT_NUMBER", "EXPIRY_DATE", "PRESCRIPTION_REQUIRED" }) {
			assertThat(after.get(col)).as(col).isEqualTo(before.get(col));
		}
	}

	@Test
	void invalidStockChangesAreRejected() throws Exception {
		int id = insert("Paracetamol", "25.50", 10, 1, LocalDate.now().plusYears(1), false);
		String back = "/admin/medicines/" + id + "/stock";

		for (String delta : new String[] { "-11", "0", "abc", "", "1000001", "-1000001", "1.5" }) {
			mockMvc.perform(admin(post(back + "/adjust").param("delta", delta)))
					.andExpect(redirectedUrl(back)).andExpect(flash().attributeExists("flashError"));
		}
		for (String stock : new String[] { "-1", "abc", "", "1000001", "2.5" }) {
			mockMvc.perform(admin(post(back + "/set").param("stock", stock)))
					.andExpect(redirectedUrl(back)).andExpect(flash().attributeExists("flashError"));
		}
		mockMvc.perform(admin(post(back + "/adjust").param("delta", "-11")))
				.andExpect(flash().attribute("flashError", containsString("cannot go below zero")));
		assertThat(stock(id)).isEqualTo(10);
	}

	// ---------------------------------------------------------------- concurrency with real purchases

	private int runAll(List<Callable<Boolean>> tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch go = new CountDownLatch(1);
		List<Future<Boolean>> futures = new ArrayList<>();
		for (Callable<Boolean> t : tasks) {
			futures.add(pool.submit(() -> {
				go.await();
				try {
					return t.call();
				} catch (PurchaseRejectedException | MedicineAdminException ex) {
					return false;
				}
			}));
		}
		go.countDown();
		int wins = 0;
		for (Future<Boolean> f : futures) {
			if (f.get()) {
				wins++;
			}
		}
		pool.shutdown();
		return wins;
	}

	@Test
	void restockingWhileCustomersBuyLosesNoUpdates() throws Exception {
		int id = insert("Busy", "10.00", 5, 1, LocalDate.now().plusYears(1), false);
		List<Callable<Boolean>> tasks = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			tasks.add(() -> purchaseService.completePurchase(id, 1, Mode.SUCCESS).paymentSucceeded());
			tasks.add(() -> adminService.adjustStock(id, 1) > 0);
		}
		runAll(tasks);

		// 5 initial + 5 restocked - 5 sold = 5, and exactly 5 sales were recorded
		assertThat(count("transactions")).isEqualTo(5);
		assertThat(stock(id)).isEqualTo(5);
	}

	@Test
	void adminRemovalsNeverMakeStockNegative() throws Exception {
		int id = insert("Scarce", "10.00", 1, 1, LocalDate.now().plusYears(1), false);
		List<Callable<Boolean>> tasks = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			tasks.add(() -> adminService.adjustStock(id, -1) >= 0);
		}
		int wins = runAll(tasks);

		assertThat(wins).isEqualTo(1);
		assertThat(stock(id)).isZero();
	}

	@Test
	void purchasesAndAdminRemovalsTogetherNeverOversell() throws Exception {
		int id = insert("Contested", "10.00", 3, 1, LocalDate.now().plusYears(1), false);
		List<Callable<Boolean>> tasks = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			tasks.add(() -> purchaseService.completePurchase(id, 1, Mode.SUCCESS).paymentSucceeded());
			tasks.add(() -> adminService.adjustStock(id, -1) >= 0);
		}
		int wins = runAll(tasks);

		assertThat(wins).isEqualTo(3);              // only 3 units existed
		assertThat(stock(id)).isZero();             // never negative
		assertThat(count("transactions")).isLessThanOrEqualTo(3);
	}
}
