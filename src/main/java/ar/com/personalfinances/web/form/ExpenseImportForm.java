package ar.com.personalfinances.web.form;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ExpenseImportForm {

    private List<ExpenseImportItem> expenses;
}