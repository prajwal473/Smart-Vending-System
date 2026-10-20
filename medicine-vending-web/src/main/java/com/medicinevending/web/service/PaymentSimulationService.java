package com.medicinevending.web.service;

import java.math.BigDecimal;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SIMULATED payment only. No payment gateway, card, UPI or network call exists here.
 * The customer chooses the simulated outcome on the confirmation page so both paths can be demonstrated.
 */
@Service
public class PaymentSimulationService {

	private static final Logger log = LoggerFactory.getLogger(PaymentSimulationService.class);

	public enum Mode {
		SUCCESS,
		FAILURE;

		/** Returns null for anything that is not exactly SUCCESS or FAILURE. */
		public static Mode fromParameter(String value) {
			if (value == null) {
				return null;
			}
			try {
				return Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException ex) {
				return null;
			}
		}
	}

	public record PaymentResult(boolean successful, String message) {
	}

	public PaymentResult process(Mode mode, BigDecimal amount) {
		if (mode == Mode.SUCCESS) {
			log.info("Simulated payment SUCCESS for amount {}", amount);
			return new PaymentResult(true, "Simulated payment approved.");
		}
		log.info("Simulated payment FAILURE for amount {}", amount);
		return new PaymentResult(false, "Simulated payment was declined.");
	}
}
