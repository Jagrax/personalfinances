package ar.com.personalfinances.util;

import ar.com.personalfinances.entity.AccountType;
import ar.com.personalfinances.web.model.FilterChip;
import ar.com.personalfinances.web.model.FilterOperator;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ExpenseSearch {

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFrom;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateTo;
    private String description;
    private String originalDescription;
    private BigDecimal amount;
    private BigDecimal amountFrom;
    private BigDecimal amountTo;
    private String details;
    private String comments;
    private Long categoryId;
    private String categoryName;
    private Long accountId;
    private String accountName;
    private AccountType accountType;
    private Long userId;
    private FilterOperator descriptionOperator = FilterOperator.CONTAINS;
    private FilterOperator originalDescriptionOperator = FilterOperator.CONTAINS;
    private FilterOperator detailsOperator = FilterOperator.CONTAINS;
    private FilterOperator commentsOperator = FilterOperator.CONTAINS;

    public boolean hasFilters() {
        return date != null ||
                dateFrom != null ||
                dateTo != null ||
                StringUtils.hasText(description) ||
                StringUtils.hasText(originalDescription) ||
                amount != null ||
                amountFrom != null ||
                amountTo != null ||
                StringUtils.hasText(details) ||
                StringUtils.hasText(comments) ||
                categoryId != null ||
                accountId != null;
    }

    public List<FilterChip> getActiveFilters() {
        List<FilterChip> chips = new ArrayList<>();

        if (date != null) chips.add(new FilterChip("date", "Fecha", FilterOperator.EQ, DateUtils.format(date)));
        if (dateFrom != null) chips.add(new FilterChip("dateFrom", "Fecha", FilterOperator.GTE, DateUtils.format(dateFrom)));
        if (dateTo != null) chips.add(new FilterChip("dateTo", "Fecha", FilterOperator.LTE, DateUtils.format(dateTo)));
        if (StringUtils.hasText(description) || FilterOperator.EMPTY.equals(descriptionOperator) || FilterOperator.NOT_EMPTY.equals(descriptionOperator)) chips.add(new FilterChip("description", "Descripcion", descriptionOperator, description));
        if (StringUtils.hasText(originalDescription) || FilterOperator.EMPTY.equals(originalDescriptionOperator) || FilterOperator.NOT_EMPTY.equals(originalDescriptionOperator)) chips.add(new FilterChip("originalDescription", "Origen", originalDescriptionOperator, originalDescription));
        if (StringUtils.hasText(details) || FilterOperator.EMPTY.equals(detailsOperator) || FilterOperator.NOT_EMPTY.equals(detailsOperator)) chips.add(new FilterChip("details", "Detalle", detailsOperator, details));
        if (StringUtils.hasText(comments) || FilterOperator.EMPTY.equals(commentsOperator) || FilterOperator.NOT_EMPTY.equals(commentsOperator)) chips.add(new FilterChip("comments", "Comentario", commentsOperator, comments));
        if (amount != null) chips.add(new FilterChip("amount", "Importe", FilterOperator.EQ, amount.toString()));
        if (amountFrom != null) chips.add(new FilterChip("amountFrom", "Importe", FilterOperator.GTE, amountFrom.toString()));
        if (amountTo != null) chips.add(new FilterChip("amountTo", "Importe", FilterOperator.LTE, amountTo.toString()));
        if (categoryId != null) chips.add(new FilterChip("categoryId", "Categoria", FilterOperator.EQ, categoryId.toString()));
        if (accountId != null) chips.add(new FilterChip("accountId", "Cuenta", FilterOperator.EQ, accountId.toString()));

        return chips;
    }
}
