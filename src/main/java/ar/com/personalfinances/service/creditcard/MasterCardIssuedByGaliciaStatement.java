package ar.com.personalfinances.service.creditcard;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.CardPeriod;
import ar.com.personalfinances.entity.Expense;
import ar.com.personalfinances.service.CardPeriodService;
import ar.com.personalfinances.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MasterCardIssuedByGaliciaStatement extends AbstractCreditCardStatement {

    public MasterCardIssuedByGaliciaStatement(CardPeriodService cardPeriodService) {
        super(cardPeriodService);
    }

    @Override
    public CreditCardStatementFormat getFormat() {
        return CreditCardStatementFormat.MASTER_CARD_GALICIA;
    }

    @Override
    public CreditCardStatementResult parse(Account account, String pdfName, List<String> pdfLines) {
        final List<CardPeriod> periodsFromPdf = resolvePeriodsFromSixDates(account, pdfLines);
        final CardPeriod currentPeriod = resolveCurrentPeriod(account, pdfName, periodsFromPdf);

        final List<Expense> expensesFromPDF = new ArrayList<>();
        boolean isConsumptionSection = false;
        for (String line : pdfLines) {
            if (isConsumptionSection) {
                // Si llegue a la linea que dice "Cuotas a vencer", entonces ya termine de leer los consumos del resumen
                if (line.startsWith("Cuotas a vencer")) break;

                Matcher matcher = CONSUMPTION_PATTERN.matcher(line);
                if (matcher.find()) {
                    String description = matcher.group(2).trim();

                    // descartar pagos en moneda extranjera
                    if (isForeignCurrency(description)) continue;

                    String amount = matcher.group(6).replace(".", "").replace(",", ".");         // 13.600,00

                    String rawDate = matcher.group(1);
                    for (Map.Entry<String, String> entry : DateUtils.MONTHS_ES.entrySet()) {
                        rawDate = rawDate.replace(entry.getKey(), entry.getValue());
                    }

                    LocalDate date;
                    try {
                        date = LocalDate.parse(rawDate, MASTERCARD_DATE_FORMATTER_EN);
                    } catch (DateTimeParseException e) {
                        throw new IllegalArgumentException("Fecha inválida: " + matcher.group(1));
                    }

                    Expense expenseFromPDF = new Expense();
                    expenseFromPDF.setDate(date);
                    expenseFromPDF.setDescription(description);
                    expenseFromPDF.setOriginalDescription(description);
                    expenseFromPDF.setAmount(new BigDecimal(amount));
                    String quotaNum = matcher.group(3);
                    String quotaDen = matcher.group(4);
                    if (quotaNum != null && quotaDen != null) {
                        int cuotaActual = Integer.parseInt(quotaNum);
                        expenseFromPDF.setDetails("Cuota " + cuotaActual + " de " + Integer.parseInt(quotaDen));
                    }
                    expenseFromPDF.setPeriod(currentPeriod);
                    expensesFromPDF.add(expenseFromPDF);
                } else {
                    matcher = DEV_PERCEP_PATTERN.matcher(line);
                    if (matcher.find()) {
                        String description = matcher.group(1).trim();
                        String amount = matcher.group(2)
                                .replace(".", "")
                                .replace(",", ".");

                        Expense expenseFromPDF = new Expense();
                        // Los DEV/PERCEP no tienen fecha propia, se imputan al cierre del periodo que se esta procesando
                        expenseFromPDF.setDate(currentPeriod.getClosingDate());
                        expenseFromPDF.setDescription(description);
                        expenseFromPDF.setOriginalDescription(description);
                        expenseFromPDF.setAmount(new BigDecimal(amount));
                        expenseFromPDF.setPeriod(currentPeriod);

                        expensesFromPDF.add(expenseFromPDF);
                    }
                }
            } else {
                isConsumptionSection = line.startsWith("CONSOLIDADO");
            }
        }

        log.info("{}[periodo: {}, consumos: {}, cantLineas: {}]", pdfName, currentPeriod.getPeriodMonthLabel(), expensesFromPDF.size(), pdfLines.size());
        return new CreditCardStatementResult(currentPeriod, expensesFromPDF);
    }

    private final static Pattern CONSUMPTION_PATTERN = Pattern.compile(
            "^"
                    + "(\\d{2}-[A-Za-z]{3}-\\d{2})"        // 1 fecha
                    + "\\s+"
                    + "(.+?)"                              // 2 descripción (incluye números)
                    + "(?:\\s+(\\d{2})/(\\d{2}))?"         // 3-4 cuota opcional
//                      + "(?:\\s+(\\d{3,}))?"                 // referencia opcional (originalmente decia esto)
                    + "(?:\\s+(\\d{5,6}))?"                // 5 nro comprobante (ANTES del importe)
//                      + "\\s+(-?[0-9.,]+)"                   // monto principal (originalmente decia esto)
                    + "\\s+(-?[0-9.]+,[0-9]{2})"           // 6 importe principal (SIEMPRE último)
//                      + "(?:\\s+(-?[0-9.,]+))?"              // segundo monto opcional (originalmente decia esto)
                    + "(?:\\s+(-?[0-9.]+,[0-9]{2}))?"      // 7 segundo importe opcional
                    + "$"
    );
    private final static Pattern DEV_PERCEP_PATTERN = Pattern.compile(
            "^"
                    + "((?:DEV|PERCEP\\.AFIP|PERC IIBB|PERCEPCION IVA).+?)"   // 1 descripción
                    + "\\s+"
                    + "(-?[0-9.]+,[0-9]{2})"         // 2 importe
                    + "$"
    );
    private final static DateTimeFormatter MASTERCARD_DATE_FORMATTER_EN = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH);
}
