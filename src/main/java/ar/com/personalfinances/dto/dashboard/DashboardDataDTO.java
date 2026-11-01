package ar.com.personalfinances.dto.dashboard;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DashboardDataDTO {
	private List<MonthlyExpensePointDTO> monthlySeries = new ArrayList<>();
	private List<String> monthlyAccounts = new ArrayList<>();
	private List<TopMerchantDTO> topMerchants = new ArrayList<>();
	private List<TopMerchantDTO> topMerchantGroups = new ArrayList<>();
	private List<TopTagDTO> topTags = new ArrayList<>();
	private List<MonthlyExpenseDTO> monthlyExpenses = new ArrayList<>();
	private BigDecimal monthlyExpensesTotal = BigDecimal.ZERO;
	private List<CardOpenPeriodDTO> cardOpenPeriodByAccount = new ArrayList<>();
	private BigDecimal cardOpenPeriodTotal = BigDecimal.ZERO;
	private List<AccountExpenseSegmentDTO> galiciaAccounts = new ArrayList<>();
	private List<AccountExpenseSegmentDTO> sddAccounts = new ArrayList<>();
	private BigDecimal momPercent = BigDecimal.ZERO;
	private String momTrend = "NEUTRAL";
}

