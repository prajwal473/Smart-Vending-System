package com.medicinevending.web.security;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stage 5A. Only /admin/** needs a login; the whole customer site (browsing, purchase, receipts,
 * static files) stays public exactly as in Stage 4.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
	static final int MIN_PASSWORD_LENGTH = 8;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/admin/login").permitAll()
						.requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
						.anyRequest().permitAll())
				.formLogin(form -> form
						.loginPage("/admin/login")
						.loginProcessingUrl("/admin/login")
						.defaultSuccessUrl("/admin/dashboard", true)
						.failureUrl("/admin/login?error")
						.permitAll())
				.logout(logout -> logout
						.logoutUrl("/admin/logout") // POST only, CSRF-protected
						.logoutSuccessUrl("/admin/login?logout")
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies("JSESSIONID"))
				// CSRF stays ON for everything (including the admin login/logout forms) except the
				// customer purchase flow, which is already protected by its own single-use,
				// session-bound token (see PurchaseSessionState) and has no admin privileges.
				.csrf(csrf -> csrf.ignoringRequestMatchers("/purchase/**"))
				.headers(Customizer.withDefaults());
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * Builds the single administrator from environment-backed properties. There is NO default account:
	 * if nothing (or something too weak) is configured, the user store is empty and nobody can log in.
	 * The plaintext password is hashed immediately and never logged or stored.
	 */
	@Bean
	public UserDetailsService adminUserDetailsService(
			PasswordEncoder passwordEncoder,
			@Value("${app.admin.username:}") String username,
			@Value("${app.admin.password:}") String plainPassword,
			@Value("${app.admin.password-hash:}") String passwordHash) {

		String name = username == null ? "" : username.trim();
		if (name.isEmpty()) {
			log.warn("Admin login is DISABLED: ADMIN_USERNAME is not set.");
			return new InMemoryUserDetailsManager(List.of());
		}

		String hash;
		if (passwordHash != null && !passwordHash.isBlank()) {
			hash = passwordHash.trim();
			if (!hash.startsWith("$2")) {
				log.warn("Admin login is DISABLED: ADMIN_PASSWORD_HASH is not a BCrypt hash.");
				return new InMemoryUserDetailsManager(List.of());
			}
		} else if (plainPassword != null && plainPassword.length() >= MIN_PASSWORD_LENGTH) {
			hash = passwordEncoder.encode(plainPassword);
		} else {
			log.warn("Admin login is DISABLED: set ADMIN_PASSWORD (at least {} characters) or ADMIN_PASSWORD_HASH.",
					MIN_PASSWORD_LENGTH);
			return new InMemoryUserDetailsManager(List.of());
		}

		UserDetails admin = User.withUsername(name).password(hash).roles("ADMIN").build();
		log.info("Administrator account '{}' is configured.", name);
		return new InMemoryUserDetailsManager(admin);
	}
}
