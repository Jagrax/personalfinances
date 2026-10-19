package ar.com.personalfinances.service.creditcard;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.CardPeriod;
import ar.com.personalfinances.entity.Expense;
import ar.com.personalfinances.service.CardPeriodService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VisaIssuedByGaliciaStatement extends AbstractCreditCardStatement {

    public VisaIssuedByGaliciaStatement(CardPeriodService cardPeriodService) {
        super(cardPeriodService);
    }

    @Override
    public CreditCardStatementFormat getFormat() {
        return CreditCardStatementFormat.VISA_GALICIA;
    }

    @Override
    public CreditCardStatementResult parse(Account account, String pdfName, List<String> pdfLines) {
        final List<CardPeriod> periodsFromPdf = resolvePeriodsFromSixDates(account, pdfLines);
        final CardPeriod currentPeriod = resolveCurrentPeriod(account, pdfName, periodsFromPdf);

        final List<Expense> expensesFromPDF = new ArrayList<>();
        boolean startReading = false;
        for (String line : pdfLines) {
            if (!startReading && line.startsWith("CONSOLIDADO")) {
                startReading = true;
            }
            if (startReading) {
                Matcher matcher = VISA_EXPENSE_ROW_PATTERN.matcher(line);
                if (matcher.find()) {
                    String description = matcher.group(2).trim();
                    if (isForeignCurrency(description)) continue;

                    final String amountRaw = matcher.group(5);
                    BigDecimal amount = new BigDecimal(
                            amountRaw.replace(".", "")
                                    .replace(",", ".")
                                    .replace("-", "")
                    );
                    if (amountRaw.startsWith("-")) amount = amount.negate();

                    LocalDate date;
                    try {
                        date = LocalDate.parse(matcher.group(1), VISA_DATE_FORMATTER);
                    } catch (DateTimeParseException e) {
                        throw new IllegalArgumentException("Fecha inválida: " + matcher.group(1));
                    }

                    Expense expenseFromPDF = new Expense();
                    expenseFromPDF.setDate(date);
                    expenseFromPDF.setDescription(description);
                    expenseFromPDF.setOriginalDescription(description);
                    expenseFromPDF.setAmount(amount);
                    String quotaNum = matcher.group(3);
                    String quotaDen = matcher.group(4);
                    if (quotaNum != null && quotaDen != null) {
                        int cuotaActual = Integer.parseInt(quotaNum);
                        expenseFromPDF.setDetails("Cuota " + cuotaActual + " de " + Integer.parseInt(quotaDen));
                    }
                    expenseFromPDF.setPeriod(currentPeriod);
                    expensesFromPDF.add(expenseFromPDF);
                }
            }
        }

        return new CreditCardStatementResult(currentPeriod, expensesFromPDF);
    }

    private final static Pattern VISA_EXPENSE_ROW_PATTERN = Pattern.compile(
            "^"
                    + "(\\d{2}-\\d{2}-\\d{2})(?=\\s)"       // 1 fecha dd-MM-yy (seguida de espacio)
                    + "\\s+"
                    + "(?:[A-Z*]\\s+)?"                    // marcador opcional (K, F, *)
                    + "(.+?)"                              // 2 descripción
                    + "(?:\\s+(\\d{2})/(\\d{2}))?"         // 3-4 cuota NN/MM
                    + "(?:\\s+\\d{5,6})?"                  // comprobante opcional
                    + "\\s+"
                    + "(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})"  // 5 importe
                    + "\\s*$"
    );
    private final static DateTimeFormatter VISA_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yy");
}
