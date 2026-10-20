package com.medicinevending.web.admin;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.assertj.core.api.Assertions.assertThat;

import com.medicinevending.web.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** With no admin credentials configured there must be NO account at all (no public default login). */
@SpringBootTest(properties = { "app.admin.username=", "app.admin.password=", "app.admin.password-hash=" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDisabledTests {

	@Autowired MockMvc mockMvc;
	@Autowired UserDetailsService users;

	@Test
	void noCredentialsConfiguredMeansNobodyCanLogIn() throws Exception {
		mockMvc.perform(formLogin("/admin/login").user("admin").password("admin"))
				.andExpect(redirectedUrl("/admin/login?error")).andExpect(unauthenticated());
		mockMvc.perform(formLogin("/admin/login").user("user").password("password"))
				.andExpect(redirectedUrl("/admin/login?error")).andExpect(unauthenticated());
		assertThrows(UsernameNotFoundException.class, () -> users.loadUserByUsername("admin"));
	}

	@Test
	void passwordIsStoredAsBcryptHashNeverPlaintext() {
		BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
		SecurityConfig cfg = new SecurityConfig();

		UserDetails fromPlain = cfg.adminUserDetailsService(enc, "boss", "Plain-Passw0rd!", "").loadUserByUsername("boss");
		assertThat(fromPlain.getPassword()).startsWith("$2").isNotEqualTo("Plain-Passw0rd!");
		assertThat(enc.matches("Plain-Passw0rd!", fromPlain.getPassword())).isTrue();

		String hash = enc.encode("Another-Passw0rd");
		UserDetails fromHash = cfg.adminUserDetailsService(enc, "boss", "", hash).loadUserByUsername("boss");
		assertThat(fromHash.getPassword()).isEqualTo(hash);
		assertThat(fromHash.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");

		// too-short plaintext password, or a non-BCrypt "hash", disables login instead of weakening it
		assertThrows(UsernameNotFoundException.class,
				() -> cfg.adminUserDetailsService(enc, "boss", "short", "").loadUserByUsername("boss"));
		assertThrows(UsernameNotFoundException.class,
				() -> cfg.adminUserDetailsService(enc, "boss", "", "plaintext-not-a-hash").loadUserByUsername("boss"));
	}
}
