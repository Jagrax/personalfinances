package ar.com.personalfinances.util;

import ar.com.personalfinances.entity.AccountType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
public class ExpenseSearch {

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date date;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date dateFrom;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date dateTo;
    private String description;
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
}