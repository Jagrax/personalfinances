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
import java.util.Objects;

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

/**
     * Asigna a un gasto el periodo indicado, o se lo quita si {@code periodId} es null. A diferencia de
     * {@link #saveWithAudit(Expense, User)} no deduce el periodo por fecha: la correccion es manual.
     */
    @Transactional
    public void updateExpensePeriod(Long expenseId, Long periodId, User user) {
        final Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("El gasto " + expenseId + " no existe"));

        if (expense.getUser() == null || !expense.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("El gasto no pertenece al usuario");
        }

        final CardPeriod period = resolvePeriodForExpense(expense, periodId);
        final Long previousPeriodId = expense.getPeriod() != null ? expense.getPeriod().getId() : null;
        final Long newPeriodId = period != null ? period.getId() : null;
        if (Objects.equals(previousPeriodId, newPeriodId)) return;

        expense.setPeriod(period);
        expenseRepository.save(expense);

        alertEventService.saveExpenseAlert(
                EntityEvent.UPDATED,
                expense.getId(),
                "period[" + previousPeriodId + "] -> [" + newPeriodId + "]",
                user.getId());
    }

    private CardPeriod resolvePeriodForExpense(Expense expense, Long periodId) {
        if (periodId == null) return null;

        final CardPeriod period = cardPeriodService.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("El periodo " + periodId + " no existe"));

        // Un gasto solo puede apuntar a un periodo de su propia tarjeta
        if (expense.getAccount() == null || period.getAccount() == null
                || !expense.getAccount().getId().equals(period.getAccount().getId())) {
            throw new IllegalArgumentException("El periodo seleccionado pertenece a otra tarjeta");
        }
        return period;
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
