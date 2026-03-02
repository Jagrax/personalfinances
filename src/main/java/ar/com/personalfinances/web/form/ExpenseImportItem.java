package ar.com.personalfinances.web.form;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
public class ExpenseImportItem {

    private Date date;
    private String description;
    private BigDecimal amount;
    private boolean selected = true;
}