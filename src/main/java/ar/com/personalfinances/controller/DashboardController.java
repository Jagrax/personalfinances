package ar.com.personalfinances.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import ar.com.personalfinances.dto.dashboard.SegmentExpensesDTO;
import ar.com.personalfinances.dto.dashboard.TopMerchantsDTO;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.service.DashboardService;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class DashboardController {

	private final DashboardService dashboardService;

	@GetMapping({"/dashboard", "/"})
	public String getDashboard(@AuthenticationPrincipal User user,
			@RequestParam(required = false) String range,
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to,
			Model model) {

		LocalDate today = LocalDate.now();
		String resolvedRange = range != null ? range : "thisMonth";
		LocalDate dateFrom;
		LocalDate dateTo;

		if ("last30".equals(resolvedRange)) {
			dateFrom = today.minusDays(29);
			dateTo = today;
		} else if ("last90".equals(resolvedRange)) {
			dateFrom = today.minusDays(89);
			dateTo = today;
		} else if ("last12m".equals(resolvedRange)) {
			dateFrom = today.minusMonths(11).with(TemporalAdjusters.firstDayOfMonth());
			dateTo = today.with(TemporalAdjusters.lastDayOfMonth());
		} else if ("custom".equals(resolvedRange) && from != null && to != null) {
			try {
				dateFrom = LocalDate.parse(from);
				dateTo = LocalDate.parse(to);
				if (dateFrom.isAfter(dateTo)) {
					LocalDate swap = dateFrom;
					dateFrom = dateTo;
					dateTo = swap;
				}
			} catch (DateTimeParseException e) {
				resolvedRange = "thisMonth";
				dateFrom = today.with(TemporalAdjusters.firstDayOfMonth());
				dateTo = today.with(TemporalAdjusters.lastDayOfMonth());
			}
		} else {
			resolvedRange = "thisMonth";
			dateFrom = today.with(TemporalAdjusters.firstDayOfMonth());
			dateTo = today.with(TemporalAdjusters.lastDayOfMonth());
		}

		dashboardService.loadDashboard(model, user, dateFrom, dateTo, resolvedRange);

		return "dashboard";
	}

	@GetMapping("/dashboard/top-merchants")
	@ResponseBody
	public TopMerchantsDTO getTopMerchants(@AuthenticationPrincipal User user,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String merchantsSort,
			@RequestParam(required = false) String groupsSort) {

		return dashboardService.getTopMerchants(user.getId(), from, to, "amount".equals(merchantsSort),
				"amount".equals(groupsSort));
	}

	@GetMapping("/dashboard/segment-expenses")
	@ResponseBody
	public SegmentExpensesDTO getSegmentExpenses(@AuthenticationPrincipal User user,
			@RequestParam String account,
			@RequestParam int year,
			@RequestParam int month) {

		if (month < 1 || month > 12) {
			throw new IllegalArgumentException("Mes inválido: " + month);
		}

		return dashboardService.getSegmentExpenses(user.getId(), account, year, month);
	}
}
