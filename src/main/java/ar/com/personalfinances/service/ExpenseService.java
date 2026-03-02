package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.EntityEvent;
import ar.com.personalfinances.entity.Expense;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.util.ApplicationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final AlertEventService alertEventService;

    public Expense saveWithAudit(Expense expense, User user) {

        EntityEvent event = expense.getId() == null
                ? EntityEvent.CREATED
                : EntityEvent.UPDATED;

        String eventDetails = "";

        if (event.equals(EntityEvent.UPDATED)) {
            eventDetails = ApplicationUtils.getChangeLog(
                    expense,
                    expenseRepository.findById(expense.getId()).orElseThrow()
            );
        }

        expense = expenseRepository.save(expense);

        alertEventService.saveExpenseAlert(
                event,
                expense.getId(),
                eventDetails,
                user.getId()
        );

        return expense;
    }

    public void saveAllWithAudit(List<Expense> expenses, User user) {
        for (Expense expense : expenses) {
            saveWithAudit(expense, user);
        }
    }
}
