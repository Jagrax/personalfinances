package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.Category;
import ar.com.personalfinances.entity.Expense;
import ar.com.personalfinances.entity.ExpenseMapping;
import ar.com.personalfinances.exception.InvalidSearchFilterException;
import ar.com.personalfinances.util.AccountSearch;
import ar.com.personalfinances.util.CategorySearch;
import ar.com.personalfinances.util.ExpenseMappingSearch;
import ar.com.personalfinances.util.ExpenseSearch;
import ar.com.personalfinances.web.model.FilterOperator;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
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

            addStringFilter(predicates, criteriaBuilder, root.get("description"), "description", expenseSearch.getDescription(), expenseSearch.getDescriptionOperator());

            addStringFilter(predicates, criteriaBuilder, root.get("originalDescription"), "originalDescription", expenseSearch.getOriginalDescription(), expenseSearch.getOriginalDescriptionOperator());

            addStringFilter(predicates, criteriaBuilder, root.get("details"), "details", expenseSearch.getDetails(), expenseSearch.getDetailsOperator());

            addStringFilter(predicates, criteriaBuilder, root.get("comments"), "comments", expenseSearch.getComments(), expenseSearch.getCommentsOperator());

            if (expenseSearch.getCategoryId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("id"), expenseSearch.getCategoryId()));
            }

            if (expenseSearch.getCategoryName() != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("name"), expenseSearch.getCategoryName()));
            }

            if (expenseSearch.getAccountId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("account").get("id"), expenseSearch.getAccountId()));
            }

            if (expenseSearch.getAccountName() != null) {
                predicates.add(criteriaBuilder.equal(root.get("account").get("name"), expenseSearch.getAccountName()));
            }

            if (expenseSearch.getAccountType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("account").get("type"), expenseSearch.getAccountType()));
            }

            if (expenseSearch.getUserId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("user").get("id"), expenseSearch.getUserId()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void addStringFilter(List<Predicate> predicates, CriteriaBuilder cb, Path<String> path, String fieldName, String value, FilterOperator operator) {
        // Default operator
        if (operator == null) operator = FilterOperator.CONTAINS;

        switch (operator) {
            case EMPTY:
                predicates.add(cb.or(
                        cb.isNull(path),
                        cb.equal(cb.trim(path), "")
                        )
                );
                break;
            case NOT_EMPTY:
                predicates.add(cb.and(
                        cb.isNotNull(path),
                        cb.notEqual(cb.trim(path), "")
                        )
                );
            case EQ:
                if (StringUtils.hasText(value)) {
                    predicates.add(cb.equal(path, value));
                }

                break;
            case CONTAINS:
                if (StringUtils.hasText(value)) {
                    predicates.add(cb.like(path, "%" + value + "%"));
                }
                break;
            default:
                throw new InvalidSearchFilterException(fieldName, operator, value);
        }
    }

    public Specification<Account> getAccounts(AccountSearch accountSearch) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (accountSearch.getId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("id"), accountSearch.getId()));
            }

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

    public Specification<Category> getCategories(CategorySearch categorySearch) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (categorySearch.getOwnerId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("owner").get("id"), categorySearch.getOwnerId()));
            }

            if (categorySearch.getOwnerIds() != null) {
                predicates.add(root.get("owner").get("id").in(categorySearch.getOwnerIds()));
            }

            if (StringUtils.hasText(categorySearch.getName())) {
                predicates.add(criteriaBuilder.like(root.get("name"), "%" + categorySearch.getName() + "%"));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public Specification<ExpenseMapping> getExpenseMappings(ExpenseMappingSearch expenseMappingSearch) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (expenseMappingSearch.getUserId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("user").get("id"), expenseMappingSearch.getUserId()));
            }

            if (StringUtils.hasText(expenseMappingSearch.getBankDescription())) {
                predicates.add(criteriaBuilder.like(root.get("bankDescription"), "%" + expenseMappingSearch.getBankDescription() + "%"));
            }

            if (StringUtils.hasText(expenseMappingSearch.getRegexPattern())) {
                predicates.add(criteriaBuilder.like(root.get("regexPattern"), "%" + expenseMappingSearch.getRegexPattern() + "%"));
            }

            if (StringUtils.hasText(expenseMappingSearch.getNormalizedDescription())) {
                predicates.add(criteriaBuilder.like(root.get("normalizedDescription"), "%" + expenseMappingSearch.getNormalizedDescription() + "%"));
            }

            if (StringUtils.hasText(expenseMappingSearch.getDetails())) {
                predicates.add(criteriaBuilder.like(root.get("details"), "%" + expenseMappingSearch.getDetails() + "%"));
            }

            if (expenseMappingSearch.getCategoryId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("id"), expenseMappingSearch.getCategoryId()));
            }

            if (expenseMappingSearch.getEnabled() != null) {
                predicates.add(criteriaBuilder.equal(root.get("enabled"), expenseMappingSearch.getEnabled()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
