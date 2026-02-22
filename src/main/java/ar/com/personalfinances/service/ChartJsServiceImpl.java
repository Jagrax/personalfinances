package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.util.ChartDataDTO;
import ar.com.personalfinances.util.ChartDatasetDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
        final List<Object[]> results = expenseRepository.getLast30DaysSumary(account, List.of("Pago de tarjeta"));
        final List<String> labels = results.stream().map(r -> (String) r[0]).collect(Collectors.toList());
        final List<Number> dataValues = results.stream().map(r -> ((BigDecimal) r[1]).doubleValue()).collect(Collectors.toList());
        return new ChartDataDTO(labels, List.of(new ChartDatasetDTO("Gastos últimos 30 días", dataValues)));
    }
}
