package com.medicinevending.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicinevending.web.dashboard.DashboardData;
import com.medicinevending.web.dashboard.DashboardService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Stage 5B statistics, checked against hand-computed numbers on an in-memory H2 database. */
@SpringBootTest(properties = { "app.admin.username=", "app.admin.password=", "app.admin.password-hash=" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardTests {

	@Autowired MockMvc mockMvc;
	@Autowired DashboardService dashboardService;
	@Autowired JdbcTemplate jdbc;

	@BeforeEach
	void clean() {
		jdbc.update("DELETE FROM transactions");
		jdbc.update("DELETE FROM medicines");
	}

	private void medicine(String name, int stock, LocalDate expiry) {
		jdbc.update("INSERT INTO medicines (medicine_name, category, price, stock, slot_number, expiry_date, prescription_required)"
				+ " VALUES (?,?,?,?,?,?,?)", name, "Test", new BigDecimal("10.00"), stock, 1, expiry, false);
	}

	private void sale(String name, int qty, String total) {
		jdbc.update("INSERT INTO transactions (medicine_id, medicine_name, quantity, total_amount) VALUES (?,?,?,?)",
				1, name, qty, new BigDecimal(total));
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	@Test
	void statisticsMatchTheDatabase() {
		LocalDate today = LocalDate.now();
		medicine("LowA", 3, today.plusDays(100));          // low
		medicine("ExpiredB", 10, today.minusDays(1));      // expired
		medicine("EmptyC", 0, today.plusDays(10));         // low + expiring soon
		medicine("NoExpiryD", 20, null);                   // null expiry must be safe
		medicine("BoundaryE", 5, today.plusDays(30));      // 5 is NOT below 5; day 30 IS in the window
		medicine("FarF", 7, today.plusDays(31));           // outside the window
		sale("LowA", 1, "10.00");
		sale("LowA", 2, "25.50");
		sale("EmptyC", 1, "4.25");

		DashboardData d = dashboardService.load();

		assertThat(d.totalMedicines()).isEqualTo(6);
		assertThat(d.totalStock()).isEqualTo(45);
		assertThat(d.totalTransactions()).isEqualTo(3);
		assertThat(d.totalSales()).isEqualByComparingTo("39.75");

		assertThat(d.lowStockThreshold()).isEqualTo(5);
		assertThat(d.lowStockCount()).isEqualTo(2);
		assertThat(d.lowStock()).extracting("medicineName").containsExactly("EmptyC", "LowA"); // lowest stock first
		assertThat(d.expiredCount()).isEqualTo(1);
		assertThat(d.expired()).extracting("medicineName").containsExactly("ExpiredB");
		assertThat(d.expiringSoonCount()).isEqualTo(2);
		assertThat(d.expiringSoon()).extracting("medicineName").containsExactly("EmptyC", "BoundaryE");
	}

	@Test
	void recentTransactionsAreNewestFirstAndCapped() {
		for (int i = 1; i <= 12; i++) {
			sale("Item" + i, 1, "1.00");
		}
		DashboardData d = dashboardService.load();
		assertThat(d.totalTransactions()).isEqualTo(12);
		assertThat(d.recentTransactions()).hasSize(10);
		assertThat(d.recentTransactions().get(0).getMedicineName()).isEqualTo("Item12");
		assertThat(d.recentTransactions().get(9).getMedicineName()).isEqualTo("Item3");
	}

	@Test
	void emptyTablesAreHandledSafely() throws Exception {
		DashboardData d = dashboardService.load();
		assertThat(d.totalMedicines()).isZero();
		assertThat(d.totalStock()).isZero();
		assertThat(d.totalTransactions()).isZero();
		assertThat(d.totalSales()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(d.lowStock()).isEmpty();
		assertThat(d.expired()).isEmpty();
		assertThat(d.expiringSoon()).isEmpty();
		assertThat(d.recentTransactions()).isEmpty();

		mockMvc.perform(get("/admin/dashboard").with(user("tester").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("No transactions have been recorded yet.")))
				.andExpect(content().string(containsString("₹0.00")));
	}

	@Test
	void dashboardPageShowsRealFiguresAndChangesNothing() throws Exception {
		medicine("Paracetamol", 3, LocalDate.now().plusDays(5));
		medicine("Cetirizine", 40, LocalDate.now().plusYears(1));
		sale("Paracetamol", 2, "1234.50");

		mockMvc.perform(get("/admin/dashboard").with(user("tester").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("₹1,234.50")))
				.andExpect(content().string(containsString("Paracetamol")))
				.andExpect(content().string(containsString("TXN-")))
				.andExpect(content().string(containsString("Manage Medicines")))
				.andExpect(content().string(containsString("Logout")));

		assertThat(count("medicines")).isEqualTo(2);
		assertThat(count("transactions")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT SUM(stock) FROM medicines", Integer.class)).isEqualTo(43);
	}

	@Test
	void anonymousUsersNeverSeeDashboardData() throws Exception {
		medicine("SecretStockItem", 1, LocalDate.now().plusDays(1));
		mockMvc.perform(get("/admin/dashboard")).andExpect(status().is3xxRedirection());
	}
}
