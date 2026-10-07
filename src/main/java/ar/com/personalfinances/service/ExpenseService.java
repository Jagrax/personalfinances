package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.repository.TagRepository;
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
    private final TagRepository tagRepository;
    private final AccountRepository accountRepository;
    private final CardPeriodService cardPeriodService;

    public Expense saveWithAudit(Expense expense, User user) {

        EntityEvent event = expense.getId() == null
                ? EntityEvent.CREATED
                : EntityEvent.UPDATED;

        String eventDetails = "";

        if (event.equals(EntityEvent.UPDATED)) {
            final Expense currentExpense = expenseRepository.findById(expense.getId()).orElseThrow();
            final Long currentAccountId = currentExpense.getAccount() != null ? currentExpense.getAccount().getId() : null;
            final Long accountId = expense.getAccount() != null ? expense.getAccount().getId() : null;

            // El periodo no se edita, asi que conservo el que ya tenia el gasto guardado, siempre que la cuenta no haya cambiado
            if (expense.getPeriod() == null && currentAccountId != null && currentAccountId.equals(accountId)) {
                expense.setPeriod(currentExpense.getPeriod());
            }
            eventDetails = ApplicationUtils.getChangeLog(
                    expense,
                    currentExpense
            );
        }

        if (expense.getItems() != null) {
            for (ExpenseItem item : expense.getItems()) {
                item.setExpense(expense);
            }
        }

        if (expense.getPeriod() == null
                && expense.getAccount() != null
                && AccountType.CREDIT_CARD.equals(expense.getAccount().getType())) {
            expense.setPeriod(cardPeriodService.findPeriodForDate(expense.getAccount(), expense.getDate()));
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

    private boolean hasSameAccount(Expense currentExpense, Expense expense) {
        final Long currentAccountId = currentExpense.getAccount() != null ? currentExpense.getAccount().getId() : null;
        final Long accountId = expense.getAccount() != null ? expense.getAccount().getId() : null;
        return currentAccountId != null && currentAccountId.equals(accountId);
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

        List<Tag> tags = null;

        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            tags = tagRepository.findAllById(request.getTagIds());
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
            if (tags != null) expense.setTags(tags);
            if (account != null) expense.setAccount(account);
        }

        saveAllWithAudit(expenses, user);
    }
}
