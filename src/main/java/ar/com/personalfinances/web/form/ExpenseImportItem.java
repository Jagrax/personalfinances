package ar.com.personalfinances.web.form;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ExpenseImportItem {

    private LocalDate date;
    private String description;
    private String details;
    private BigDecimal amount;
    private boolean selected = true;
}