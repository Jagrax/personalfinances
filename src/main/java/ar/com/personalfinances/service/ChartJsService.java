package ar.com.personalfinances.service;

import java.time.LocalDate;

import ar.com.personalfinances.entity.Account;

public interface ChartJsService {

    LocalDate resolvePeriodStart(Account account);
}
