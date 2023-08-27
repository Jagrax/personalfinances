package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.Expense;
import ar.com.personalfinances.util.AccountSearch;
import ar.com.personalfinances.util.ExpenseSearch;
import javax.persistence.criteria.Predicate;
import org.springframework.util.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SpecificationsService {

    public Specification<Expense> getExpenses(ExpenseSearch expenseSearch) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (expenseSearch.getDate() != null) {
                predicates.add(criteriaBuilder.equal(root.get("date"), expenseSearch.getDate()));
            }

            if (expenseSearch.getDateFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("date"), expenseSearch.getDateFrom()));
            }

            if (expenseSearch.getDateTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("date"), expenseSearch.getDateTo()));
            }

            if (expenseSearch.getAmount() != null) {
                predicates.add(criteriaBuilder.equal(root.get("amount"), expenseSearch.getAmount()));
            }

            if (expenseSearch.getAmountFrom() != null) {
                predicates.add(criteriaBuilder.ge(root.get("amount"), expenseSearch.getAmountFrom()));
            }

            if (expenseSearch.getAmountTo() != null) {
                predicates.add(criteriaBuilder.le(root.get("amount"), expenseSearch.getAmountTo()));
            }

            if (StringUtils.hasText(expenseSearch.getDescription())) {
                predicates.add(criteriaBuilder.like(root.get("description"), "%" + expenseSearch.getDescription() + "%"));
            }

            if (StringUtils.hasText(expenseSearch.getDetails())) {
                predicates.add(criteriaBuilder.like(root.get("details"), "%" + expenseSearch.getDetails() + "%"));
            }

            if (StringUtils.hasText(expenseSearch.getComments())) {
                predicates.add(criteriaBuilder.like(root.get("comments"), "%" + expenseSearch.getComments() + "%"));
            }

            if (expenseSearch.getCategoryId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("id"), expenseSearch.getCategoryId()));
            }

            if (expenseSearch.getCategoryName() != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("name"), expenseSearch.getCategoryName()));
            }

            if (expenseSearch.getSourceAccountId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("sourceAccount").get("id"), expenseSearch.getSourceAccountId()));
            }

            if (expenseSearch.getSourceAccountName() != null) {
                predicates.add(criteriaBuilder.equal(root.get("sourceAccount").get("name"), expenseSearch.getSourceAccountName()));
            }

            if (expenseSearch.getSourceAccountType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("sourceAccount").get("type"), expenseSearch.getSourceAccountType()));
            }

            if (expenseSearch.getTargetAccountId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("targetAccount").get("id"), expenseSearch.getTargetAccountId()));
            }

            if (expenseSearch.getTargetAccountName() != null) {
                predicates.add(criteriaBuilder.equal(root.get("targetAccount").get("name"), expenseSearch.getTargetAccountName()));
            }

            if (expenseSearch.getTargetAccountType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("targetAccount").get("type"), expenseSearch.getTargetAccountType()));
            }

            if (expenseSearch.getAccountId() != null) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.equal(root.get("sourceAccount").get("id"), expenseSearch.getAccountId()),
                        criteriaBuilder.equal(root.get("targetAccount").get("id"), expenseSearch.getAccountId())
                ));
            }

            if (expenseSearch.getAccountName() != null) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.equal(root.get("sourceAccount").get("name"), expenseSearch.getAccountName()),
                        criteriaBuilder.equal(root.get("targetAccount").get("name"), expenseSearch.getAccountName())
                        ));
            }

            if (expenseSearch.getAccountType() != null) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.equal(root.get("sourceAccount").get("type"), expenseSearch.getAccountType()),
                        criteriaBuilder.equal(root.get("targetAccount").get("type"), expenseSearch.getAccountType())
                ));
            }

            if (expenseSearch.getUserId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("user").get("id"), expenseSearch.getUserId()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public Specification<Account> getAccounts(AccountSearch accountSearch) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (accountSearch.getOwnerId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("owner").get("id"), accountSearch.getOwnerId()));
            }

            if (accountSearch.getOwnerIds() != null) {
                predicates.add(root.get("owner").get("id").in(accountSearch.getOwnerIds()));
            }

            if (StringUtils.hasText(accountSearch.getName())) {
                predicates.add(criteriaBuilder.like(root.get("name"), "%" + accountSearch.getName() + "%"));
            }

            if (accountSearch.getAccountType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), accountSearch.getAccountType()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}