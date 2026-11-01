package ar.com.personalfinances.service;

import ar.com.personalfinances.dto.dashboard.SegmentExpensesDTO;
import ar.com.personalfinances.dto.dashboard.TopMerchantsDTO;
import ar.com.personalfinances.entity.User;
import org.springframework.ui.Model;

import java.time.LocalDate;

public interface DashboardService {
	void loadDashboard(Model model, User user, LocalDate from, LocalDate to, String range);

	TopMerchantsDTO getTopMerchants(Long userId, LocalDate from, LocalDate to, boolean merchantsByAmount,
                                    boolean groupsByAmount);

	SegmentExpensesDTO getSegmentExpenses(Long userId, String accountName, int year, int month);
}
