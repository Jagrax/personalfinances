package ar.com.personalfinances.service.creditcard;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.CardPeriod;
import ar.com.personalfinances.entity.Expense;
import ar.com.personalfinances.service.CardPeriodService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class VisaIssuedByVisaStatement extends AbstractCreditCardStatement {

    public VisaIssuedByVisaStatement(CardPeriodService cardPeriodService) {
        super(cardPeriodService);
    }

    @Override
    public CreditCardStatementFormat getFormat() {
        return CreditCardStatementFormat.VISA;
    }

    @Override
    public CreditCardStatementResult parse(Account account, String pdfName, List<String> pdfLines) {
        final List<CardPeriod> periodsFromPdf = resolveVisaPeriods(account, pdfLines);
        final CardPeriod currentPeriod = resolveCurrentPeriod(account, pdfName, periodsFromPdf);
        // Las cuotas se imputan al primer dia del periodo del resumen
        final LocalDate fixedQuotaDate = currentPeriod.getPeriodStart();

        final List<Expense> expensesFromPDF = new ArrayList<>();
        boolean startReading = false;
        for (String line : pdfLines) {
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
                    if (amountRaw.endsWith("-")) amount = amount.negate();

                    LocalDate date;
                    try {
                        date = LocalDate.parse(matcher.group(1), VISA_DATE_FORMATTER_ES);
                    } catch (DateTimeParseException e) {
                        try {
                            date = LocalDate.parse(matcher.group(1), VISA_DATE_FORMATTER_EN);
                        } catch (DateTimeParseException e2) {
                            throw new IllegalArgumentException("Fecha inválida: " + matcher.group(1));
                        }
                    }

                    Expense expenseFromPDF = new Expense();
                    expenseFromPDF.setDate(date);
                    expenseFromPDF.setDescription(description);
                    expenseFromPDF.setOriginalDescription(description);
                    expenseFromPDF.setAmount(amount);
                    String quotaNum = matcher.group(3);
                    String quotaDen = matcher.group(4);
                    if (quotaNum != null && quotaDen != null) {
                        expenseFromPDF.setDetails("Cuota " + Integer.parseInt(quotaNum) + " de " + Integer.parseInt(quotaDen));
                        if (fixedQuotaDate != null) expenseFromPDF.setDate(fixedQuotaDate);
                    }
                    expenseFromPDF.setPeriod(currentPeriod);
                    expensesFromPDF.add(expenseFromPDF);
                }
            } else {
                startReading = line.startsWith("FECHA");
            }
        }

        log.info("{}[periodo: {}, consumos: {}, cantLineas: {}]", pdfName, currentPeriod.getPeriodMonthLabel(), expensesFromPDF.size(), pdfLines.size());
        return new CreditCardStatementResult(currentPeriod, expensesFromPDF);
    }

    /**
     * Los resumenes con labels muestran el cierre actual, el vencimiento, el cierre anterior y los proximos cierre y vencimiento.
     * Cuando el vencimiento no entra en su linea (el label desborda la pagina) se toma de la linea siguiente.
     */
    private List<CardPeriod> resolveVisaPeriods(Account account, List<String> pdfLines) {
        final List<CardPeriod> periodsFromSixDates = resolvePeriodsFromSixDates(account, pdfLines);
        if (!periodsFromSixDates.isEmpty()) return periodsFromSixDates;

        LocalDate previousClosing = null, currentClosing = null, currentDue = null, nextClosing = null, nextDue = null;
        for (int i = 0; i < pdfLines.size(); i++) {
            final String line = pdfLines.get(i);

            if (currentClosing == null) {
                final Matcher matcher = VISA_CIERRE_ACTUAL_PATTERN.matcher(line);
                if (matcher.find()) currentClosing = parseSummaryDate(matcher.group(1));
            }
            if (nextClosing == null) {
                final Matcher matcher = VISA_PROXIMO_CIERRE_PATTERN.matcher(line);
                if (matcher.find()) nextClosing = parseSummaryDate(matcher.group(1));
            }
            if (nextDue == null) {
                final Matcher matcher = VISA_PROXIMO_VTO_PATTERN.matcher(line);
                if (matcher.find()) nextDue = parseSummaryDate(matcher.group(1));
            }
            if (previousClosing == null && line.startsWith("CIERRE ANTERIOR")) {
                final int valueStart = line.indexOf("CIERRE ANTERIOR") + "CIERRE ANTERIOR".length();
                final int valueEnd = line.indexOf("PAGO MIN. ANT.");
                if (valueEnd > valueStart) previousClosing = parseSummaryDate(line.substring(valueStart, valueEnd));
            }
            if (currentDue == null && line.contains("VENCIMIENTO SALDO") && i + 1 < pdfLines.size()) {
                final Matcher matcher = SUMMARY_DATE_IN_TEXT_PATTERN.matcher(pdfLines.get(i + 1));
                if (matcher.find()) currentDue = parseSummaryDate(matcher.group(1));
            }
        }

        return buildSummaryPeriods(account, previousClosing, currentClosing, currentDue, nextClosing, nextDue);
    }

    private final static Pattern VISA_EXPENSE_ROW_PATTERN = Pattern.compile(
            "^"
                    + "(\\d{2}\\.\\d{2}\\.\\d{2})"          // Fecha
                    + "\\s+"
                    + "(?:\\d{5,6}[A-Z*]?\\s+)?"            // Comprobante (opcional)
                    + "(.+?)"                               // Detalle de transaccion
                    + "(?:\\s+Cuota\\s+(\\d{2})/(\\d{2}))?" // Detalle de la cuota (opcional)
                    + "\\s+"
                    + "(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}-?)" // Importe
                    + "\\s*$"
    );
    private final static DateTimeFormatter VISA_DATE_FORMATTER_ES = DateTimeFormatter.ofPattern("dd.MM.yy", new Locale("es"));
    private final static DateTimeFormatter VISA_DATE_FORMATTER_EN = DateTimeFormatter.ofPattern("dd.MM.yy", Locale.ENGLISH);

    private final static Pattern VISA_CIERRE_ACTUAL_PATTERN = Pattern.compile("CIERRE ACTUAL:?\\s*(\\d{1,2}\\s*[A-Za-z]{3}\\s*\\d{2})");
    private final static Pattern VISA_PROXIMO_CIERRE_PATTERN = Pattern.compile("PROXIMO CIERRE:?\\s*(\\d{1,2}\\s*[A-Za-z]{3}\\s*\\d{2})");
    private final static Pattern VISA_PROXIMO_VTO_PATTERN = Pattern.compile("PROXIMO VTO\\.?:?\\s*(\\d{1,2}\\s*[A-Za-z]{3}\\s*\\d{2})");
    private final static Pattern SUMMARY_DATE_IN_TEXT_PATTERN = Pattern.compile("(\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{2})");
}
