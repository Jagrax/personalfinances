package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.CategoryRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.util.ApplicationUtils;
import ar.com.personalfinances.web.model.BulkExpenseUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final AlertEventService alertEventService;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;

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

    @Transactional
    public void bulkUpdateExpenses(BulkExpenseUpdateRequest request, User user) {
        if (request.getExpenseIds() == null || request.getExpenseIds().isEmpty()) {
            throw new IllegalArgumentException("No expenses selected");
        }

        List<Expense> expenses = expenseRepository.findAllById(request.getExpenseIds());

        if (expenses.size() != request.getExpenseIds().size()) {
            throw new IllegalArgumentException("Some expenses were not found");
        }

        Category category = null;

        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId()).orElseThrow();
        }

        Account account = null;
        if (request.getAccountId() != null) {
            account = accountRepository.findById(request.getAccountId()).orElseThrow();
        }

        for (Expense expense : expenses) {
            if (!expense.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Expense does not belong to user");
            }

            if (request.getDate() != null) expense.setDate(request.getDate());
            if (request.getDescription() != null) expense.setDescription(request.getDescription());
            if (request.getOriginalDescription() != null) expense.setOriginalDescription(request.getOriginalDescription());
            if (request.getDetails() != null) expense.setDetails(request.getDetails());
            if (request.getComments() != null) expense.setComments(request.getComments());
            if (category != null) expense.setCategory(category);
            if (account != null) expense.setAccount(account);
        }

        saveAllWithAudit(expenses, user);
    }
}
