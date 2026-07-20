package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.util.ChartDataDTO;

public interface ChartJsService {

    ChartDataDTO buildExpensesSumaryByTagChart(Account account);
}
