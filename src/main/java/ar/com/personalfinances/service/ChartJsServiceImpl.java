package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.AccountType;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.util.ChartDataDTO;
import ar.com.personalfinances.util.ChartDatasetDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChartJsServiceImpl implements ChartJsService {

    private final ExpenseRepository expenseRepository;

    @Autowired
    public ChartJsServiceImpl(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    public ChartDataDTO buildExpensesSumaryByCategoryChart(Account account) {
        final List<Object[]> results;
        final LocalDate periodStart = resolvePeriodStart(account);
        if (account.getType().equals(AccountType.BANK_ACCOUNT)) {
            results = expenseRepository.getLast30DaysSummaryForBankAccount(account, List.of("Pago de haberes", "Pago de tarjeta"), "%FIMA%");
        } else if (account.getType().equals(AccountType.CREDIT_CARD)) {
            results = expenseRepository.getLastPeriodSummaryForCreditCard(account, periodStart, List.of(13L, -1L));
        } else if (account.getName().equals("SDD")) {
            List<Object[]> lastPeriodSummaryForSDD = expenseRepository.getLastPeriodSummaryForSDD(account, periodStart);

            BigDecimal totalPeriodo = lastPeriodSummaryForSDD.stream()
                    .map(r -> (BigDecimal) r[1])
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalHistorico = expenseRepository.sumByAccount(account);
            if (totalHistorico == null) totalHistorico = BigDecimal.ZERO;

            BigDecimal saldoAnterior = totalHistorico.subtract(totalPeriodo);

            if (saldoAnterior.compareTo(BigDecimal.ZERO) != 0) {
                lastPeriodSummaryForSDD.add(new Object[]{
                        saldoAnterior.signum() > 0
                                ? "Saldo pendiente anterior"
                                : "Excedente anterior (no se muestra en el grafico por ser negativo)",
                        saldoAnterior
                });
            }
            results = lastPeriodSummaryForSDD;
        } else {
            results = expenseRepository.getLast30DaysSummary(account, List.of("Pago de tarjeta"));
        }
        final List<String> labels = results.stream().map(r -> (String) r[0]).collect(Collectors.toList());
        final List<Number> dataValues = results.stream().map(r -> ((BigDecimal) r[1]).doubleValue()).collect(Collectors.toList());
        return new ChartDataDTO(labels, List.of(new ChartDatasetDTO("Gastos últimos 30 días", dataValues)));
    }

    public LocalDate resolvePeriodStart(Account account) {
        if (account.getType().equals(AccountType.CREDIT_CARD)) {
            LocalDate today = LocalDate.now();
            if (account.getClosingDay() == null) {
                // fallback → 1er daa del mes
                return today.withDayOfMonth(1);
            }

            int closingDay = account.getClosingDay();
            LocalDate thisMonthClosing = today.withDayOfMonth(
                    Math.min(closingDay, today.lengthOfMonth())
            );

            if (today.isAfter(thisMonthClosing)) {
                return thisMonthClosing.plusDays(1);
            } else {
                return thisMonthClosing.minusMonths(1).plusDays(1);
            }
        } else if (account.getName().equals("SDD")) {
            final Date lastReimbursementDate = expenseRepository.findLastReimbursementDate(account);
            return lastReimbursementDate != null ? lastReimbursementDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null;
        } else {
            return LocalDate.now().minusMonths(1);
        }
    }
}