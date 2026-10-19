package ar.com.personalfinances.service.creditcard;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.CardPeriod;
import ar.com.personalfinances.service.CardPeriodService;
import ar.com.personalfinances.util.DateUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractCreditCardStatement implements CreditCardStatement {

    protected final CardPeriodService cardPeriodService;

    protected AbstractCreditCardStatement(CardPeriodService cardPeriodService) {
        this.cardPeriodService = cardPeriodService;
    }

    protected CardPeriod resolveCurrentPeriod(Account account, String pdfName, List<CardPeriod> periodsFromPdf) {
        cardPeriodService.syncPeriodsFromSummaries(account, periodsFromPdf);
        return requirePersistedPeriod(account, periodsFromPdf, pdfName);
    }

    /**
     * Devuelve el periodo persistido del resumen. Si el resumen no resuelve periodo, o el periodo no existe
     * en la DB, es un error: no se puede saber contra que gastos hay que emparejar.
     */
    protected CardPeriod requirePersistedPeriod(Account account, List<CardPeriod> periodsFromPdf, String pdfName) {
        if (periodsFromPdf.isEmpty() || periodsFromPdf.getFirst().getClosingDate() == null) {
            throw new IllegalArgumentException("No se pudo resolver el periodo del resumen " + pdfName);
        }

        final CardPeriod period = cardPeriodService.findPeriodForDate(account, periodsFromPdf.getFirst().getClosingDate());
        if (period == null || period.getPeriodStart() == null) {
            throw new IllegalArgumentException("El periodo del resumen " + pdfName + " no esta dado de alta en la DB");
        }
        return period;
    }

    /**
     * Los resumenes de Galicia traen 6 fechas correlativas: cierre del resumen anterior, su vencimiento,
     * cierre del resumen actual, su vencimiento, cierre del resumen siguiente y su vencimiento.
     */
    protected List<CardPeriod> resolvePeriodsFromSixDates(Account account, List<String> pdfLines) {
        LocalDate[] dates = null;
        for (int i = 0; i < pdfLines.size(); i++) {
            String line = pdfLines.get(i);
            Matcher matcher = SIX_DATES_PATTERN.matcher(line);
            if (matcher.find()) {
                dates = new LocalDate[matcher.groupCount()];
                for (int d = 0; d < dates.length; d++) {
                    dates[d] = parseSummaryDate(matcher.group(d + 1));
                }
                break;
            }
            // Si la linea tiene 5 fechas y la siguiente linea empieza con 2 digitos, es un salto de linea en la ultima fecha
            Matcher partialMatcher = SIX_DATES_PARTIAL_PATTERN.matcher(line);
            if (partialMatcher.find() && i + 1 < pdfLines.size()) {
                String nextLine = pdfLines.get(i + 1).trim();
                if (nextLine.matches("^\\d{2}$")) {
                    matcher = SIX_DATES_PATTERN.matcher(line + nextLine);
                    if (matcher.find()) {
                        dates = new LocalDate[matcher.groupCount()];
                        for (int d = 0; d < dates.length; d++) {
                            dates[d] = parseSummaryDate(matcher.group(d + 1));
                        }
                        break;
                    }
                }
            }
        }
        if (dates == null) return List.of();

        return buildSummaryPeriods(account, dates[0], dates[2], dates[3], dates[4], dates[5]);
    }

    /**
     * Arma el periodo del resumen actual (cierre anterior + 1 dia .. cierre actual) y el del resumen siguiente,
     * para no tener que esperar al proximo PDF.
     */
    protected List<CardPeriod> buildSummaryPeriods(Account account, LocalDate previousClosing, LocalDate currentClosing, LocalDate currentDue, LocalDate nextClosing, LocalDate nextDue) {
        if (currentClosing == null) return List.of();

        final List<CardPeriod> periods = new ArrayList<>();
        periods.add(buildSummaryPeriod(account, previousClosing != null ? previousClosing.plusDays(1) : null, currentClosing, currentDue));
        if (nextClosing != null) {
            periods.add(buildSummaryPeriod(account, currentClosing.plusDays(1), nextClosing, nextDue));
        }
        return periods;
    }

    protected CardPeriod buildSummaryPeriod(Account account, LocalDate periodStart, LocalDate closingDate, LocalDate dueDate) {
        final CardPeriod period = new CardPeriod();
        period.setAccount(account);
        period.setPeriodStart(periodStart);
        period.setClosingDate(closingDate);
        period.setDueDate(dueDate);
        return period;
    }

    /**
     * Parsea las fechas de los resumenes, que vienen como dd-MMM-yy (dd-Nov-24) o dd MMM yy (01 Oct 26),
     * a veces con las letras separadas por espacios (2 9 E n e 2 6).
     */
    protected LocalDate parseSummaryDate(String rawDate) {
        if (!StringUtils.hasText(rawDate)) return null;

        final Matcher matcher = SUMMARY_DATE_PATTERN.matcher(rawDate.replaceAll("[\\s.-]", ""));
        if (!matcher.matches()) return null;

        final String monthRaw = matcher.group(2);
        final String monthKey = monthRaw.substring(0, 1).toUpperCase(Locale.ROOT) + monthRaw.substring(1).toLowerCase(Locale.ROOT);
        final String month = DateUtils.MONTHS_ES.get(monthKey);
        if (month == null) return null;

        try {
            return LocalDate.parse(matcher.group(1) + "-" + month + "-" + matcher.group(3), SUMMARY_DATE_FORMATTER_EN);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    final List<String> foreignCurrencies = List.of(
            "USD",
            "U$S",
            "USA",
            "EUR",
            "CLP",
            "UYU",
            "DEU"
    );
    /**
     * Los gastos en monedas distintas a la de la cuenta no se importan. Los resumenes los identifican
     * con el codigo de la moneda en la descripcion: USD, U$S, EUR, CLP (chilenos) y UYU (uruguayos).
     */
    protected boolean isForeignCurrency(String description) {
        for (String foreignCurrency : foreignCurrencies) {
            if (description.contains(foreignCurrency)) {
                int currPos = description.indexOf(foreignCurrency);

                if (currPos == 0) {
                    // Hasta ahora no vi ningún gasto en moneda extranjera cuya descripción comience con la moneda
                    return false;
                }

                char prevChar = description.charAt(currPos - 1);
                if (Character.isLetter(prevChar)) {
                    return false;
                }

                int nextPos = currPos + foreignCurrency.length();
                if (nextPos < description.length()) {
                    char nextChar = description.charAt(nextPos);
                    if (Character.isLetter(nextChar)) {
                        return false;
                    }
                }

                return true;
            }
        }

        return false;
    }

    protected final static Pattern SIX_DATES_PATTERN = Pattern.compile(
            "(\\d{2}-[A-Za-zÁÉÍÓÚáéíóúñÑ]{3,9}-\\d{2})\\s+" +
                    "(\\d{2}-[A-Za-zÁÉÍÓÚáéíóúñÑ]{3,9}-\\d{2})\\s+" +
                    "(\\d{2}-[A-Za-zÁÉÍÓÚáéíóúñÑ]{3,9}-\\d{2})\\s+" +
                    "(\\d{2}-[A-Za-zÁÉÍÓÚáéíóúñÑ]{3,9}-\\d{2})\\s+" +
                    "(\\d{2}-[A-Za-zÁÉÍÓÚáéíóúñÑ]{3,9}-\\d{2})\\s+" +
                    "(\\d{2}-[A-Za-zÁÉÍÓÚáéíóúñÑ]{3,9}-\\d{2})"
    );
    protected final static Pattern SIX_DATES_PARTIAL_PATTERN = Pattern.compile(
            "(\\d{2}-[A-Za-zÁÉÍÓÚáéíóúñÑ]{3,9}-\\d{2})\\s+" +
                    "(\\d{2}-[A-Za-zÁÉÍÓÚáéíóúñÑ]{3,9}-\\d{2})\\s+" +
                    "(\\d{2}-[A-Za-zÁÉÍÓÚáéíóúñÑ]{3,9}-\\d{2})\\s+" +
                    "(\\d{2}-[A-Za-zÁÉÍÓÚáéíóúñÑ]{3,9}-\\d{2})\\s+" +
                    "(\\d{2}-[A-Za-zÁÉÍÓÚáéíóúñÑ]{3,9}-\\d{2})\\s+" +
                    "(\\d{2}-[A-Za-zÁÉÍÓÚáéíóúñÑ]{3,9}-)$"
    );
    protected final static Pattern SUMMARY_DATE_PATTERN = Pattern.compile("(\\d{1,2})([A-Za-zÁÉÍÓÚáéíóúñÑ]{3,9})(\\d{2})");
    protected final static DateTimeFormatter SUMMARY_DATE_FORMATTER_EN = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH);
    protected final static Locale PDF_LOCALE = new Locale("es", "AR");
    protected final static DateTimeFormatter FORMATTER_ARG = DateTimeFormatter.ofPattern("dd-MMM-yy", PDF_LOCALE);
}
