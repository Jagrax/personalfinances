package ar.com.personalfinances.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import ar.com.personalfinances.dto.dashboard.AccountExpenseSegmentDTO;
import ar.com.personalfinances.dto.dashboard.CardOpenPeriodDTO;
import ar.com.personalfinances.dto.dashboard.DashboardDataDTO;
import ar.com.personalfinances.dto.dashboard.ExpenseDetailDTO;
import ar.com.personalfinances.dto.dashboard.MonthlyExpensePointDTO;
import ar.com.personalfinances.dto.dashboard.RecurringExpenseDTO;
import ar.com.personalfinances.dto.dashboard.RecurringExpenseItemDTO;
import ar.com.personalfinances.dto.dashboard.RecurringExpensePointDTO;
import ar.com.personalfinances.dto.dashboard.SegmentExpensesDTO;
import ar.com.personalfinances.dto.dashboard.TopMerchantDTO;
import ar.com.personalfinances.dto.dashboard.TopMerchantsDTO;
import ar.com.personalfinances.dto.dashboard.TopTagDTO;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.repository.DashboardRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

	private final DashboardRepository dashboardRepository;

	private static final int TOP_SIZE = 10;
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final Pattern INSTALLMENT_PATTERN = Pattern.compile("Cuota\\s+(\\d+)\\s+de\\s+(\\d+)");

	@Override
	public void loadDashboard(Model model, User user, LocalDate from, LocalDate to, String range) {
		DashboardDataDTO data = new DashboardDataDTO();

		// El resumen de gastos y la variación de tarjetas miran el mes en curso y no el rango de los tops: el rango
		// mueve las métricas de abajo, no lo que se gastó este mes.
		LocalDate monthStart = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
		LocalDate monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth());

		buildCardOpenPeriod(data, user);
		BigDecimal total = nonNull(dashboardRepository.getTotalForPeriod(user.getId(), from, to));
		BigDecimal momPercent = calculateCardMom(user, monthStart, monthEnd);

		data.setMomPercent(momPercent);
		data.setMomTrend(trendOf(momPercent));
		data.setMonthlySeries(buildMonthlySeries(user, to, data));
		long totalMovements = dashboardRepository.countMovementsForPeriod(user.getId(), from, to);
		data.setTopMerchants(buildTopMerchants(user.getId(), from, to, totalMovements, false));
		data.setTopMerchantGroups(buildTopMerchantGroups(user.getId(), from, to, totalMovements, false));
		data.setTopTags(buildTopTags(user, from, to, total));
		buildRecurringExpenses(data, user, to);
		buildAccountSegments(data, user, monthStart, monthEnd);

		model.addAttribute("dashboardData", data);
		model.addAttribute("selectedRange", range != null ? range : "thisMonth");
		model.addAttribute("dateFrom", from);
		model.addAttribute("dateTo", to);
	}

	@Override
	public TopMerchantsDTO getTopMerchants(Long userId, LocalDate from, LocalDate to, boolean merchantsByAmount,
			boolean groupsByAmount) {
		long totalMovements = dashboardRepository.countMovementsForPeriod(userId, from, to);
		return new TopMerchantsDTO(buildTopMerchants(userId, from, to, totalMovements, merchantsByAmount),
				buildTopMerchantGroups(userId, from, to, totalMovements, groupsByAmount));
	}

	private void buildAccountSegments(DashboardDataDTO data, User user, LocalDate monthStart, LocalDate monthEnd) {
		data.setGaliciaAccounts(buildAccountExpenseSegments(
				dashboardRepository.getGaliciaAccountExpenses(user.getId(), monthStart, monthEnd)));
		data.setSddAccounts(buildAccountExpenseSegments(
				dashboardRepository.getSddAccountExpenses(user.getId())));
	}

	@Override
	public SegmentExpensesDTO getSegmentExpenses(Long userId, String accountName, int year, int month) {
		YearMonth period = YearMonth.of(year, month);
		List<Object[]> rows = dashboardRepository.getSegmentExpenses(userId, accountName, period.atDay(1),
				period.atEndOfMonth());

		SegmentExpensesDTO result = new SegmentExpensesDTO();
		for (Object[] r : rows) {
			Long id = r[0] != null ? ((Number) r[0]).longValue() : null;
			LocalDate date = toLocalDate(r[1]);
			String description = r[2] != null ? r[2].toString() : "";
			String details = r[3] != null ? r[3].toString() : null;
			String tags = r[4] != null ? r[4].toString() : null;
			BigDecimal amount = r[5] != null ? new BigDecimal(r[5].toString()) : BigDecimal.ZERO;
			result.setCreditCard(r[6] != null && "CREDIT_CARD".equals(r[6].toString()));
			result.getExpenses().add(new ExpenseDetailDTO(id, date != null ? DATE_FORMATTER.format(date) : null,
					description, details, extractInstallment(details), tags, amount.setScale(2, RoundingMode.HALF_UP)));
		}
		return result;
	}

	private String extractInstallment(String details) {
		if (details == null) {
			return null;
		}
		Matcher matcher = INSTALLMENT_PATTERN.matcher(details);
		return matcher.find() ? matcher.group(1) + " de " + matcher.group(2) : null;
	}

	private List<AccountExpenseSegmentDTO> buildAccountExpenseSegments(List<Object[]> rows) {
		List<AccountExpenseSegmentDTO> list = new ArrayList<>();
		for (Object[] r : rows) {
			Long accountId = r[0] != null ? ((Number) r[0]).longValue() : null;
			String name = r[1] != null ? r[1].toString() : "";
			String currency = r[2] != null ? r[2].toString() : null;
			String icon = r[4] != null ? r[4].toString() : null;
			Long bankId = r[5] != null ? ((Number) r[5]).longValue() : null;
			BigDecimal total = r[7] != null ? new BigDecimal(r[7].toString()) : BigDecimal.ZERO;
			LocalDateTime lastSyncAt = null;
			if (r.length > 6 && r[6] != null) {
				if (r[6] instanceof java.sql.Timestamp ts) {
					lastSyncAt = ts.toLocalDateTime();
				} else if (r[6] instanceof LocalDateTime ldt) {
					lastSyncAt = ldt;
				}
			}
			String label = name;
			if (currency != null && !name.toUpperCase(Locale.ROOT).contains(currency.toUpperCase(Locale.ROOT))) {
				label += " " + currency;
			}
			list.add(new AccountExpenseSegmentDTO(accountId, name, currency,
					total.setScale(2, RoundingMode.HALF_UP), label, icon, bankId, lastSyncAt));
		}
		return list;
	}

	private void buildCardOpenPeriod(DashboardDataDTO data, User user) {
		List<Object[]> rows = dashboardRepository.getOpenPeriodCardConsumptionByAccount(user.getId());
		List<CardOpenPeriodDTO> list = new ArrayList<>();
		BigDecimal total = BigDecimal.ZERO;
		for (Object[] r : rows) {
			Long accountId = r[0] != null ? ((Number) r[0]).longValue() : null;
			String name = r[1] != null ? r[1].toString() : "";
			BigDecimal val = r[2] != null ? new BigDecimal(r[2].toString()).setScale(2, RoundingMode.HALF_UP)
					: BigDecimal.ZERO;
			LocalDate closing = toLocalDate(r[3]);
			LocalDate due = toLocalDate(r[4]);
			String icon = r[5] != null ? r[5].toString() : null;
			total = total.add(val);
			list.add(new CardOpenPeriodDTO(accountId, name, val, closing, due, icon));
		}
		data.setCardOpenPeriodByAccount(list);
		data.setCardOpenPeriodTotal(total.setScale(2, RoundingMode.HALF_UP));
	}

	private LocalDate toLocalDate(Object value) {
		if (value instanceof java.sql.Date sqlDate) {
			return sqlDate.toLocalDate();
		}
		if (value instanceof LocalDate localDate) {
			return localDate;
		}
		return null;
	}

	/**
	 * Variación del consumo de tarjetas del mes en curso contra el mes anterior. El número que tiene al lado es el
	 * período abierto actual de las tarjetas; con ventanas de 30 o 90 días la comparación no era contra un período
	 * equivalente y el porcentaje no se podía leer.
	 */
	private BigDecimal calculateCardMom(User user, LocalDate monthStart, LocalDate monthEnd) {
		BigDecimal current = nonNull(dashboardRepository.getCardTotalForPeriod(user.getId(), monthStart, monthEnd));
		BigDecimal prev = nonNull(
				dashboardRepository.getCardTotalForPeriod(user.getId(), monthStart.minusMonths(1), monthEnd.minusMonths(1)));
		if (prev.signum() == 0) {
			return BigDecimal.ZERO;
		}
		return current.subtract(prev).multiply(BigDecimal.valueOf(100)).divide(prev, 2, RoundingMode.HALF_UP);
	}

	private String trendOf(BigDecimal value) {
		if (value == null || value.signum() == 0) {
			return "NEUTRAL";
		}
		return value.signum() > 0 ? "UP" : "DOWN";
	}

	private BigDecimal nonNull(BigDecimal value) {
		return value != null ? value : BigDecimal.ZERO;
	}

	private List<MonthlyExpensePointDTO> buildMonthlySeries(User user, LocalDate to, DashboardDataDTO data) {
		LocalDate seriesFrom = to.minusMonths(11).with(TemporalAdjusters.firstDayOfMonth());

		List<Object[]> rows = dashboardRepository.getMonthlySeriesByAccount(user.getId(), seriesFrom, to);

		Map<String, Map<String, BigDecimal>> map = new LinkedHashMap<>();
		for (LocalDate d = seriesFrom; !d.isAfter(to.withDayOfMonth(1)); d = d.plusMonths(1)) {
			String key = d.getYear() + "-" + d.getMonthValue();
			map.put(key, new LinkedHashMap<>());
		}

		Set<String> accounts = new LinkedHashSet<>();
		for (Object[] r : rows) {
			int year = ((Number) r[0]).intValue();
			int month = ((Number) r[1]).intValue();
			String accountName = r[2] != null ? r[2].toString() : "";
			BigDecimal total = r[3] != null ? new BigDecimal(r[3].toString()) : BigDecimal.ZERO;
			String key = year + "-" + month;
			Map<String, BigDecimal> values = map.get(key);
			if (values != null) {
				values.merge(accountName, total.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
				accounts.add(accountName);
			}
		}

		List<String> orderedAccounts = accounts.stream()
				.sorted(Comparator.comparingInt(this::accountRank).thenComparing(Comparator.naturalOrder()))
				.collect(Collectors.toList());
		data.setMonthlyAccounts(orderedAccounts);

		List<MonthlyExpensePointDTO> list = new ArrayList<>();
		for (Map.Entry<String, Map<String, BigDecimal>> e : map.entrySet()) {
			String[] ym = e.getKey().split("-");
			int y = Integer.parseInt(ym[0]);
			int m = Integer.parseInt(ym[1]);
			Map<String, BigDecimal> vals = new LinkedHashMap<>();
			BigDecimal monthTotal = BigDecimal.ZERO;
			for (String account : orderedAccounts) {
				BigDecimal v = e.getValue().getOrDefault(account, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
				vals.put(account, v);
				monthTotal = monthTotal.add(v);
			}
			String label = String.format("%02d/%d", m, y);
			list.add(new MonthlyExpensePointDTO(y, m, label, vals, monthTotal.setScale(2, RoundingMode.HALF_UP)));
		}
		return list;
	}

	private int accountRank(String name) {
		String n = name != null ? name.toUpperCase(Locale.ROOT) : "";
		if (n.startsWith("GALICIA")) {
			return 0;
		}
		if (n.equals("VISA")) {
			return 1;
		}
		if (n.startsWith("MASTER")) {
			return 2;
		}
		return 3;
	}

	private List<TopMerchantDTO> buildTopMerchants(Long userId, LocalDate from, LocalDate to, long totalMovements,
			boolean byAmount) {
		return toTopMerchants(dashboardRepository.getTopMerchants(userId, from, to), totalMovements, byAmount);
	}

	private List<TopMerchantDTO> buildTopMerchantGroups(Long userId, LocalDate from, LocalDate to, long totalMovements,
			boolean byAmount) {
		return toTopMerchants(dashboardRepository.getTopMerchantGroups(userId, from, to), totalMovements, byAmount);
	}

	private List<TopMerchantDTO> toTopMerchants(List<Object[]> rows, long totalMovements, boolean byAmount) {
		BigDecimal denominator = totalMovements > 0 ? BigDecimal.valueOf(totalMovements) : BigDecimal.ONE;
		List<TopMerchantDTO> list = new ArrayList<>();
		for (Object[] r : rows) {
			String merchant = r[0] != null ? r[0].toString() : "(Sin descripción)";
			BigDecimal total = r[1] != null ? new BigDecimal(r[1].toString()) : BigDecimal.ZERO;
			long count = r[2] != null ? ((Number) r[2]).longValue() : 0L;
			BigDecimal pct = BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100)).divide(denominator, 2,
					RoundingMode.HALF_UP);
			list.add(new TopMerchantDTO(merchant, total.setScale(2, RoundingMode.HALF_UP), count, pct));
		}

		Comparator<TopMerchantDTO> byCount = Comparator.comparingLong(TopMerchantDTO::getCount).reversed()
				.thenComparing(Comparator.comparing(TopMerchantDTO::getTotal).reversed());
		Comparator<TopMerchantDTO> byTotal = Comparator.comparing(TopMerchantDTO::getTotal).reversed()
				.thenComparing(Comparator.comparingLong(TopMerchantDTO::getCount).reversed());

		list.sort(byAmount ? byTotal : byCount);
		return list.size() > TOP_SIZE ? new ArrayList<>(list.subList(0, TOP_SIZE)) : list;
	}

	private List<TopTagDTO> buildTopTags(User user, LocalDate from, LocalDate to, BigDecimal totalPeriodo) {
		List<Object[]> rows = dashboardRepository.getTopTags(user.getId(), from, to);
		BigDecimal denominator = totalPeriodo.signum() == 0 ? BigDecimal.ONE : totalPeriodo;
		List<TopTagDTO> list = new ArrayList<>();
		for (Object[] r : rows) {
			Long tagId = r[0] != null ? ((Number) r[0]).longValue() : null;
			String name = r[1] != null ? r[1].toString() : "(Sin tag)";
			String color = r[2] != null ? r[2].toString() : null;
			BigDecimal total = r[3] != null ? new BigDecimal(r[3].toString()) : BigDecimal.ZERO;
			BigDecimal pct = total.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
			list.add(new TopTagDTO(tagId, name, color, total.setScale(2, RoundingMode.HALF_UP), pct));
		}
		return list;
	}

	private void buildRecurringExpenses(DashboardDataDTO data, User user, LocalDate to) {
		LocalDate from = to.minusMonths(11).with(TemporalAdjusters.firstDayOfMonth());

		Map<String, List<RecurringPayment>> paymentsByConcept = new LinkedHashMap<>();
		for (Object[] r : dashboardRepository.getRecurringExpenses(user.getId(), from, to)) {
			String concept = r[0] != null ? r[0].toString() : "";
			LocalDate date = toLocalDate(r[1]);
			if (date == null) continue;
			BigDecimal amount = r[2] != null ? new BigDecimal(r[2].toString()) : BigDecimal.ZERO;
			paymentsByConcept.computeIfAbsent(concept, k -> new ArrayList<>())
					.add(new RecurringPayment(date, amount.setScale(2, RoundingMode.HALF_UP)));
		}

		List<String> concepts = paymentsByConcept.entrySet().stream()
				.sorted(Comparator.comparing((Map.Entry<String, List<RecurringPayment>> e) -> e.getValue().stream()
						.map(RecurringPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add)).reversed())
				.map(Map.Entry::getKey)
				.toList();

		Map<String, Map<String, BigDecimal>> totalsByConcept = new LinkedHashMap<>();
		List<RecurringExpenseDTO> table = new ArrayList<>();
		for (String concept : concepts) {
			List<RecurringPayment> payments = paymentsByConcept.get(concept);
			payments.sort(Comparator.comparing(RecurringPayment::date).reversed());

			String lastPeriod = periodLabel(payments.getFirst().date());
			BigDecimal lastAmount = payments.stream()
					.filter(p -> periodLabel(p.date()).equals(lastPeriod))
					.map(RecurringPayment::amount)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			List<RecurringExpenseItemDTO> previous = payments.stream()
					.filter(p -> !periodLabel(p.date()).equals(lastPeriod))
					.map(p -> new RecurringExpenseItemDTO(DATE_FORMATTER.format(p.date()), p.amount()))
					.toList();
			table.add(new RecurringExpenseDTO(concept, DATE_FORMATTER.format(payments.getFirst().date()), lastAmount,
					previous));

			Map<String, BigDecimal> monthly = new LinkedHashMap<>();
			for (RecurringPayment p : payments) {
				monthly.merge(periodKey(p.date()), p.amount(), BigDecimal::add);
			}
			totalsByConcept.put(concept, monthly);
		}

		data.setRecurringExpenses(table);
		data.setRecurringSeries(buildRecurringSeries(from, to, concepts, totalsByConcept));
	}

	private List<RecurringExpensePointDTO> buildRecurringSeries(LocalDate from, LocalDate to, List<String> concepts,
			Map<String, Map<String, BigDecimal>> totalsByConcept) {
		List<RecurringExpensePointDTO> series = new ArrayList<>();
		for (LocalDate d = from; !d.isAfter(to.withDayOfMonth(1)); d = d.plusMonths(1)) {
			String ym = periodKey(d);
			Map<String, BigDecimal> values = new LinkedHashMap<>();
			BigDecimal monthTotal = BigDecimal.ZERO;
			for (String concept : concepts) {
				BigDecimal value = totalsByConcept.get(concept).getOrDefault(ym, BigDecimal.ZERO)
						.setScale(2, RoundingMode.HALF_UP);
				values.put(concept, value);
				monthTotal = monthTotal.add(value);
			}
			series.add(new RecurringExpensePointDTO(d.getYear(), d.getMonthValue(), periodLabel(d), values,
					monthTotal.setScale(2, RoundingMode.HALF_UP)));
		}
		return series;
	}

	private String periodKey(LocalDate date) {
		return date.getYear() + "-" + date.getMonthValue();
	}

	private String periodLabel(LocalDate date) {
		return String.format("%02d/%d", date.getMonthValue(), date.getYear());
	}

	private record RecurringPayment(LocalDate date, BigDecimal amount) {
	}
}
