package com.medicinevending.web.admin;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stage 5A. In-memory H2 (profile "test") and a throw-away test-only admin; never the real database. */
@SpringBootTest(properties = {
		"app.admin.username=testadmin",
		"app.admin.password=Test-Only-Passw0rd" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthTests {

	static final String USER = "testadmin";
	static final String PASS = "Test-Only-Passw0rd";

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbc;

	@BeforeEach
	void seed() {
		jdbc.update("DELETE FROM transactions");
		jdbc.update("DELETE FROM medicines");
		jdbc.update("INSERT INTO medicines (medicine_name, category, price, stock, slot_number, expiry_date, prescription_required)"
				+ " VALUES (?,?,?,?,?,?,?)", "Paracetamol", "Test", new BigDecimal("25.50"), 10, 1,
				LocalDate.now().plusYears(1), false);
	}

	private MockHttpSession login() throws Exception {
		MvcResult r = mockMvc.perform(formLogin("/admin/login").user(USER).password(PASS))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/dashboard"))
				.andExpect(authenticated().withUsername(USER)).andReturn();
		return (MockHttpSession) r.getRequest().getSession(false);
	}

	@Test
	void applicationContextLoadsAndLoginPageIsPublic() throws Exception {
		mockMvc.perform(get("/admin/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Username")))
				.andExpect(content().string(containsString("Password")))
				.andExpect(content().string(containsString("name=\"_csrf\"")));
	}

	@Test
	void unauthenticatedUsersCannotReachAdminPages() throws Exception {
		for (String url : new String[] { "/admin", "/admin/", "/admin/dashboard", "/admin/anything" }) {
			mockMvc.perform(get(url))
					.andExpect(status().is3xxRedirection())
					// Spring Security 7 issues a RELATIVE redirect to the login page
					.andExpect(redirectedUrl("/admin/login"))
					.andExpect(unauthenticated());
		}
	}

	@Test
	void invalidCredentialsAreRejectedWithGenericMessage() throws Exception {
		mockMvc.perform(formLogin("/admin/login").user(USER).password("wrong-password"))
				.andExpect(redirectedUrl("/admin/login?error"))
				.andExpect(unauthenticated());
		mockMvc.perform(formLogin("/admin/login").user("nobody").password(PASS))
				.andExpect(redirectedUrl("/admin/login?error"))
				.andExpect(unauthenticated());
		mockMvc.perform(get("/admin/login").param("error", ""))
				.andExpect(content().string(containsString("Invalid username or password.")));
	}

	@Test
	void loginWithoutCsrfTokenIsRejected() throws Exception {
		mockMvc.perform(post("/admin/login").param("username", USER).param("password", PASS))
				.andExpect(status().isForbidden());
	}

	@Test
	void validCredentialsGiveAccessToDashboard() throws Exception {
		MockHttpSession session = login();
		mockMvc.perform(get("/admin/dashboard").session(session))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Dashboard")))
				.andExpect(content().string(containsString(USER)))
				.andExpect(content().string(not(containsString(PASS))));
	}

	@Test
	void logoutEndsTheSession() throws Exception {
		MockHttpSession session = login();
		mockMvc.perform(post("/admin/logout").session(session).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/login?logout"));
		assertThat(session.isInvalid()).isTrue();
		// the old session id no longer grants access
		mockMvc.perform(get("/admin/dashboard").session(session))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/login"))
				.andExpect(unauthenticated());
	}

	@Test
	void logoutRequiresPost() throws Exception {
		MockHttpSession session = login();
		mockMvc.perform(get("/admin/logout").session(session));
		mockMvc.perform(get("/admin/dashboard").session(session)).andExpect(status().isOk());
	}

	@Test
	void publicKioskStillWorksWithoutLogin() throws Exception {
		mockMvc.perform(get("/")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Paracetamol")));
		mockMvc.perform(get("/medicines")).andExpect(status().isOk());
		mockMvc.perform(get("/css/home.css")).andExpect(status().isOk());
		// purchase flow is reachable anonymously (400 = its own validation, not a login redirect / 403)
		mockMvc.perform(get("/purchase/confirm")).andExpect(status().isBadRequest());
		mockMvc.perform(post("/purchase/pay").param("token", "x").param("outcome", "HACK"))
				.andExpect(status().isBadRequest());
	}
}