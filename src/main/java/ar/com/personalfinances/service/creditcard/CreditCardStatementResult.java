package ar.com.personalfinances.service.creditcard;

import ar.com.personalfinances.entity.CardPeriod;
import ar.com.personalfinances.entity.Expense;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CreditCardStatementResult {

    private final CardPeriod period;
    private final List<Expense> expenses;
}
