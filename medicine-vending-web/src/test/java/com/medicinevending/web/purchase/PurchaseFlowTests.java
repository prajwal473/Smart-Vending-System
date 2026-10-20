package com.medicinevending.web.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicinevending.web.service.PaymentSimulationService.Mode;
import com.medicinevending.web.service.PurchaseRejectedException;
import com.medicinevending.web.service.PurchaseService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Runs against an in-memory H2 database (profile "test"). Never touches the real MySQL database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseFlowTests {

	@Autowired MockMvc mockMvc;
	@Autowired PurchaseService purchaseService;
	@Autowired JdbcTemplate jdbc;

	int otcId;      // Paracetamol, price 25.50, stock 10
	int rxId;       // prescription required
	int expiredId;  // expired
	int emptyId;    // stock 0
	int lastOneId;  // stock 1

	@BeforeEach
	void seed() {
		jdbc.update("DELETE FROM transactions");
		jdbc.update("DELETE FROM medicines");
		otcId = insert("Paracetamol", "25.50", 10, LocalDate.now().plusYears(1), false);
		rxId = insert("Antibiotic", "80.00", 10, LocalDate.now().plusYears(1), true);
		expiredId = insert("Old Syrup", "40.00", 10, LocalDate.now().minusDays(1), false);
		emptyId = insert("Empty Pack", "10.00", 0, LocalDate.now().plusYears(1), false);
		lastOneId = insert("Last One", "5.00", 1, LocalDate.now().plusYears(1), false);
	}

	private int insert(String name, String price, int stock, LocalDate expiry, boolean rx) {
		jdbc.update("INSERT INTO medicines (medicine_name, category, price, stock, slot_number, expiry_date, prescription_required)"
				+ " VALUES (?,?,?,?,?,?,?)", name, "Test", new BigDecimal(price), stock, 1, expiry, rx);
		return jdbc.queryForObject("SELECT MAX(medicine_id) FROM medicines", Integer.class);
	}

	private int stock(int id) {
		return jdbc.queryForObject("SELECT stock FROM medicines WHERE medicine_id=?", Integer.class, id);
	}

	private int txCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class);
	}

	private String confirmAndGetToken(MockHttpSession session, int id, int qty) throws Exception {
		MvcResult r = mockMvc.perform(get("/purchase/confirm").session(session)
				.param("medicineId", String.valueOf(id)).param("quantity", String.valueOf(qty)))
				.andExpect(status().isOk()).andReturn();
		String html = r.getResponse().getContentAsString();
		int i = html.indexOf("name=\"token\" value=\"") + 20;
		return html.substring(i, html.indexOf('"', i));
	}

	// ---------------------------------------------------------------- calculation & validation

	@Test
	void confirmPageShowsServerCalculatedTotal() throws Exception {
		mockMvc.perform(get("/purchase/confirm").param("medicineId", String.valueOf(otcId)).param("quantity", "3"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("₹76.50")));
	}

	@Test
	void clientSuppliedPriceIsIgnored() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String token = confirmAndGetToken(session, otcId, 2);
		mockMvc.perform(post("/purchase/pay").session(session).param("token", token)
				.param("outcome", "SUCCESS").param("price", "0.01").param("total", "0.01")
				.param("medicineId", String.valueOf(rxId)).param("quantity", "9"))
				.andExpect(status().is3xxRedirection());
		assertThat(jdbc.queryForObject("SELECT total_amount FROM transactions", BigDecimal.class))
				.isEqualByComparingTo("51.00");
		assertThat(jdbc.queryForObject("SELECT medicine_id FROM transactions", Integer.class)).isEqualTo(otcId);
		assertThat(stock(otcId)).isEqualTo(8);
		assertThat(stock(rxId)).isEqualTo(10);
	}

	@Test
	void rejectsInvalidRequests() throws Exception {
		for (String qty : new String[] { "0", "-1", "abc", "", "99999999999" }) {
			mockMvc.perform(get("/purchase/confirm").param("medicineId", String.valueOf(otcId)).param("quantity", qty))
					.andExpect(status().isBadRequest());
		}
		mockMvc.perform(get("/purchase/confirm").param("medicineId", "999999").param("quantity", "1"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/purchase/confirm")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/purchase/confirm").param("medicineId", String.valueOf(otcId)).param("quantity", "11"))
				.andExpect(status().isConflict());
	}

	@Test
	void rejectsExpiredOutOfStockAndPrescriptionMedicines() throws Exception {
		mockMvc.perform(get("/purchase/confirm").param("medicineId", String.valueOf(rxId)).param("quantity", "1"))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/purchase/confirm").param("medicineId", String.valueOf(expiredId)).param("quantity", "1"))
				.andExpect(status().isConflict());
		mockMvc.perform(get("/purchase/confirm").param("medicineId", String.valueOf(emptyId)).param("quantity", "1"))
				.andExpect(status().isConflict());
		// And the service itself refuses them even if someone skips the confirm page:
		assertThatThrownBy(() -> purchaseService.completePurchase(rxId, 1, Mode.SUCCESS))
				.isInstanceOf(PurchaseRejectedException.class);
		assertThatThrownBy(() -> purchaseService.completePurchase(expiredId, 1, Mode.SUCCESS))
				.isInstanceOf(PurchaseRejectedException.class);
		assertThat(txCount()).isZero();
		assertThat(stock(rxId)).isEqualTo(10);
	}

	// ---------------------------------------------------------------- payment outcomes

	@Test
	void successfulPaymentDeductsStockRecordsTransactionAndShowsReceipt() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String token = confirmAndGetToken(session, otcId, 4);
		MvcResult r = mockMvc.perform(post("/purchase/pay").session(session).param("token", token).param("outcome", "SUCCESS"))
				.andExpect(redirectedUrlPattern("/purchase/receipt/*")).andReturn();

		assertThat(stock(otcId)).isEqualTo(6);
		assertThat(txCount()).isEqualTo(1);

		mockMvc.perform(get(r.getResponse().getRedirectedUrl()).session(session))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("TXN-")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("₹102.00")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Paracetamol")));
	}

	@Test
	void failedPaymentChangesNothing() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String token = confirmAndGetToken(session, otcId, 4);
		mockMvc.perform(post("/purchase/pay").session(session).param("token", token).param("outcome", "FAILURE"))
				.andExpect(status().isPaymentRequired())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Payment failed")));
		assertThat(stock(otcId)).isEqualTo(10);
		assertThat(txCount()).isZero();
	}

	@Test
	void unknownOutcomeIsRejected() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String token = confirmAndGetToken(session, otcId, 1);
		mockMvc.perform(post("/purchase/pay").session(session).param("token", token).param("outcome", "HACK"))
				.andExpect(status().isBadRequest());
		assertThat(stock(otcId)).isEqualTo(10);
	}

	// ---------------------------------------------------------------- duplicates & overselling

	@Test
	void submittingTheSameFormTwiceBuysOnlyOnce() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String token = confirmAndGetToken(session, otcId, 2);
		MvcResult first = mockMvc.perform(post("/purchase/pay").session(session).param("token", token).param("outcome", "SUCCESS"))
				.andExpect(status().is3xxRedirection()).andReturn();
		MvcResult second = mockMvc.perform(post("/purchase/pay").session(session).param("token", token).param("outcome", "SUCCESS"))
				.andExpect(status().is3xxRedirection()).andReturn();

		assertThat(second.getResponse().getRedirectedUrl()).isEqualTo(first.getResponse().getRedirectedUrl());
		assertThat(txCount()).isEqualTo(1);
		assertThat(stock(otcId)).isEqualTo(8);
	}

	@Test
	void tokenFromAnotherSessionIsRejected() throws Exception {
		String token = confirmAndGetToken(new MockHttpSession(), otcId, 1);
		mockMvc.perform(post("/purchase/pay").session(new MockHttpSession()).param("token", token).param("outcome", "SUCCESS"))
				.andExpect(status().isConflict());
		assertThat(txCount()).isZero();
	}

	@Test
	void receiptIsNotVisibleToOtherSessions() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String token = confirmAndGetToken(session, otcId, 1);
		MvcResult r = mockMvc.perform(post("/purchase/pay").session(session).param("token", token).param("outcome", "SUCCESS"))
				.andReturn();
		mockMvc.perform(get(r.getResponse().getRedirectedUrl()).session(new MockHttpSession()))
				.andExpect(status().isNotFound());
	}

	@Test
	void stockChangingAfterConfirmPageIsCaughtAtPayment() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String token = confirmAndGetToken(session, otcId, 5);
		jdbc.update("UPDATE medicines SET stock = 2 WHERE medicine_id = ?", otcId); // someone else bought stock
		mockMvc.perform(post("/purchase/pay").session(session).param("token", token).param("outcome", "SUCCESS"))
				.andExpect(status().isConflict());
		assertThat(stock(otcId)).isEqualTo(2);
		assertThat(txCount()).isZero();
	}

	@Test
	void twoBuyersRacingForTheLastItemCannotOversell() throws Exception {
		int buyers = 6;
		ExecutorService pool = Executors.newFixedThreadPool(buyers);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<Boolean>> results = new java.util.ArrayList<>();
		for (int i = 0; i < buyers; i++) {
			Callable<Boolean> task = () -> {
				go.await();
				try {
					return purchaseService.completePurchase(lastOneId, 1, Mode.SUCCESS).paymentSucceeded();
				} catch (PurchaseRejectedException ex) {
					return false;
				}
			};
			results.add(pool.submit(task));
		}
		go.countDown();
		int wins = 0;
		for (Future<Boolean> f : results) {
			if (f.get()) {
				wins++;
			}
		}
		pool.shutdown();

		assertThat(wins).isEqualTo(1);
		assertThat(stock(lastOneId)).isZero();
		assertThat(txCount()).isEqualTo(1);
	}
}
