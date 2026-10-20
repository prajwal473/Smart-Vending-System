package com.medicinevending.web.controller;

import com.medicinevending.web.dashboard.DashboardService;
import java.security.Principal;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Stage 5: admin login page and dashboard. Authorization is enforced in SecurityConfig, not here. */
@Controller
public class AdminController {

	private final DashboardService dashboardService;

	public AdminController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping("/admin/login")
	public String login(Authentication authentication) {
		if (authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken)) {
			return "redirect:/admin/dashboard";
		}
		return "admin/login";
	}

	@GetMapping("/admin")
	public String adminRoot() {
		return "redirect:/admin/dashboard";
	}

	@GetMapping("/admin/dashboard")
	public String dashboard(Principal principal, Model model) {
		model.addAttribute("adminName", principal == null ? "" : principal.getName());
		model.addAttribute("d", dashboardService.load());
		return "admin/dashboard";
	}
}
