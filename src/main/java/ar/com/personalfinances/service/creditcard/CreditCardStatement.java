package ar.com.personalfinances.service.creditcard;

import ar.com.personalfinances.entity.Account;

import java.util.List;

public interface CreditCardStatement {

    CreditCardStatementFormat getFormat();

    CreditCardStatementResult parse(Account account, String pdfName, List<String> pdfLines);
}
