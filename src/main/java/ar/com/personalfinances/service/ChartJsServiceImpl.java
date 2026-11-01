package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.AccountType;
import ar.com.personalfinances.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ChartJsServiceImpl implements ChartJsService {

    private final ExpenseRepository expenseRepository;

    @Autowired
    public ChartJsServiceImpl(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @Override
    public LocalDate resolvePeriodStart(Account account) {
        if (account.getType().equals(AccountType.CREDIT_CARD)) {
            if (account.getClosingDay() == null) {
                return LocalDate.now().withDayOfMonth(1);
            }

            return account.getClosingDay().plusDays(1);
        } else if (account.getName().equals("SDD")) {
            return expenseRepository.findLastReimbursementDate(account);
        } else {
            return LocalDate.now().minusMonths(1);
        }
    }
}
