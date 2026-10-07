package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.service.*;
import ar.com.personalfinances.util.*;
import ar.com.personalfinances.web.form.ExpenseImportForm;
import ar.com.personalfinances.web.form.ExpenseImportItem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Controller
public class BankSyncController {

    private final SpecificationsService specificationsService;
    private final AccountRepository accountRepository;
    private final ApplicationMessageService applicationMessageService;
    private final AccountManagementService accountManagementService;
    private final PDFService pdfService;
    private final ExpenseRepository expenseRepository;
    private final ExpenseService expenseService;
    private final CardPeriodService cardPeriodService;

    public BankSyncController(SpecificationsService specificationsService, AccountRepository accountRepository, ApplicationMessageService applicationMessageService, AccountManagementService accountManagementService, PDFService pdfService, ExpenseRepository expenseRepository, ExpenseService expenseService, CardPeriodService cardPeriodService) {
        this.specificationsService = specificationsService;
        this.accountRepository = accountRepository;
        this.applicationMessageService = applicationMessageService;
        this.accountManagementService = accountManagementService;
        this.pdfService = pdfService;
        this.expenseRepository = expenseRepository;
        this.expenseService = expenseService;
        this.cardPeriodService = cardPeriodService;
    }

    @RequestMapping(value = "/bank-learn", method = RequestMethod.POST)
    public String postBankLearn(@Valid BankSyncModelAttribute bankSyncModelAttribute, HttpServletRequest request) {
        String backUrl = ApplicationUtils.getCurrentPage(request, false);
        if (!StringUtils.hasText(backUrl)) {
            backUrl = "/expenses";
        }

        if (bankSyncModelAttribute.getAccountId() == null && bankSyncModelAttribute.getAccountName() == null) {
            applicationMessageService.add(request, ApplicationMessage.error("AccountId and AccountName are null"));
            return "redirect:" + backUrl;
        } else {
            Optional<Account> optionalAccount;
            if (bankSyncModelAttribute.getAccountId() != null) {
                optionalAccount = accountRepository.findById(bankSyncModelAttribute.getAccountId());
                if (optionalAccount.isEmpty()) {
                    applicationMessageService.add(request, ApplicationMessage.error("Invalid account"));
                    return "redirect:" + backUrl;
                }
            } else {
                List<Long> accountSearchOwnerIds = new ArrayList<>();
                accountSearchOwnerIds.add(-1L); // La cuenta Generica la pueden utilizar todos los usuarios

                User user = ApplicationUtils.getUserFromSession(false);
                if (user != null) {
                    // Si no tengo al usuario, no puedo ver ninguna cuenta mas que la -1
                    accountSearchOwnerIds.add(user.getId());
                }

                AccountSearch accountSearch = new AccountSearch();
                accountSearch.setOwnerIds(accountSearchOwnerIds);
                accountSearch.setName(bankSyncModelAttribute.getAccountName());
                List<Account> userAccounts = accountRepository.findAll(specificationsService.getAccounts(accountSearch), Sort.by(Sort.Direction.ASC, "name"));
                if (CollectionUtils.isEmpty(userAccounts)) {
                    applicationMessageService.add(request, ApplicationMessage.error("Invalid account"));
                    return "redirect:" + backUrl;
                }
                optionalAccount = userAccounts.stream().findFirst();
            }

            final Account account = optionalAccount.get();
            if (!account.getType().equals(AccountType.BANK_ACCOUNT)) {
                applicationMessageService.add(request, ApplicationMessage.error("Invalid account type: " + account.getType()));
                return "redirect:" + backUrl;
            }

            CommonResult learnFromBankMovementsResult = accountManagementService.learnFromBankMovements(account, bankSyncModelAttribute.getGaliciaCookies());
            if (learnFromBankMovementsResult.isError() || learnFromBankMovementsResult.isWarning()) {
                applicationMessageService.add(request, ApplicationMessage.error(learnFromBankMovementsResult.getMessage()));
                return "redirect:" + backUrl;
            } else {
                applicationMessageService.add(request, ApplicationMessage.success(learnFromBankMovementsResult.getMessage()));

            }

            // TODO: Definir una pagina para mostrar los resultados
            //noinspection SpringMVCViewInspection
            return "redirect:/expenses?accountType=" + account.getType().name() + "&accountName=" + account.getName();
        }
    }

    @RequestMapping(value = "/bank-sync", method = RequestMethod.POST)
    public String postBankSync(@Valid BankSyncModelAttribute bankSyncModelAttribute, HttpServletRequest request) {
        String backUrl = ApplicationUtils.getCurrentPage(request, false);
        if (!StringUtils.hasText(backUrl)) {
            backUrl = "/expenses";
        }

        if (bankSyncModelAttribute.getAccountId() == null && bankSyncModelAttribute.getAccountName() == null) {
            applicationMessageService.add(request, ApplicationMessage.error("AccountId and AccountName are null"));
            return "redirect:" + backUrl;
        } else {
            Optional<Account> optionalAccount;
            if (bankSyncModelAttribute.getAccountId() != null) {
                optionalAccount = accountRepository.findById(bankSyncModelAttribute.getAccountId());
                if (optionalAccount.isEmpty()) {
                    applicationMessageService.add(request, ApplicationMessage.error("Invalid account"));
                    return "redirect:" + backUrl;
                }
            } else {
                List<Long> accountSearchOwnerIds = new ArrayList<>();
                accountSearchOwnerIds.add(-1L); // La cuenta Generica la pueden utilizar todos los usuarios

                User user = ApplicationUtils.getUserFromSession(false);
                if (user != null) {
                    // Si no tengo al usuario, no puedo ver ninguna cuenta mas que la -1
                    accountSearchOwnerIds.add(user.getId());
                }

                AccountSearch accountSearch = new AccountSearch();
                accountSearch.setOwnerIds(accountSearchOwnerIds);
                accountSearch.setName(bankSyncModelAttribute.getAccountName());
                List<Account> userAccounts = accountRepository.findAll(specificationsService.getAccounts(accountSearch), Sort.by(Sort.Direction.ASC,"name"));
                if (CollectionUtils.isEmpty(userAccounts)) {
                    applicationMessageService.add(request, ApplicationMessage.error("Invalid account"));
                    return "redirect:" + backUrl;
                }
                optionalAccount = userAccounts.stream().findFirst();
            }

            final Account account = optionalAccount.get();

            switch (account.getType()) {
                case CREDIT_CARD: {
                    CommonResult syncResult = accountManagementService.syncCreditCardAccountMovements(account, bankSyncModelAttribute.getGaliciaCookies());
                    if (syncResult.isError() || syncResult.isWarning()) {
                        applicationMessageService.add(request, ApplicationMessage.error(syncResult.getMessage()));
                        return "redirect:" + backUrl;
                    } else {
                        SyncResult payload = (SyncResult) syncResult.getPayload();
                        if (payload != null && payload.hasUnmatched()) {
                            applicationMessageService.add(request, ApplicationMessage.warn(syncResult.getMessage()));
                        } else {
                            applicationMessageService.add(request, ApplicationMessage.success(syncResult.getMessage()));
                        }
                    }
                    break;
                }
                case BANK_ACCOUNT: {
                    CommonResult syncAccountMovementsResult = accountManagementService.syncAccountMovements(account, bankSyncModelAttribute.getGaliciaCookies());
                    if (syncAccountMovementsResult.isError() || syncAccountMovementsResult.isWarning()) {
                        applicationMessageService.add(request, ApplicationMessage.error(syncAccountMovementsResult.getMessage()));
                        return "redirect:" + backUrl;
                    } else {
                        SyncResult payload = (SyncResult) syncAccountMovementsResult.getPayload();
                        if (payload != null && payload.hasUnmatched()) {
                            applicationMessageService.add(request, ApplicationMessage.warn(syncAccountMovementsResult.getMessage()));
                        } else {
                            applicationMessageService.add(request, ApplicationMessage.success(syncAccountMovementsResult.getMessage()));
                        }
                    }
                    break;
                }
                default:
                    applicationMessageService.add(request, ApplicationMessage.error("Invalid account type: " + account.getType()));
                    return "redirect:" + backUrl;
            }

            //noinspection SpringMVCViewInspection
            return "redirect:/expenses?accountType=" + account.getType().name() + "&accountName=" + account.getName();
        }
    }

    @RequestMapping(value = "/bank-sync/bank/{bankId}", method = RequestMethod.POST)
    public String postBankSyncByBank(@PathVariable("bankId") Long bankId,
                                      @RequestParam("galiciaCookies") String galiciaCookies,
                                      HttpServletRequest request) {
        List<Account> accounts = accountRepository.findByBank_Id(bankId);
        List<SyncResult> syncResults = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Account account : accounts) {
            if (!account.syncEnabled) continue;
            try {
                CommonResult syncResult;
                if (account.getType().equals(AccountType.CREDIT_CARD)) {
                    syncResult = accountManagementService.syncCreditCardAccountMovements(account, galiciaCookies);
                } else if (account.getType().equals(AccountType.BANK_ACCOUNT)) {
                    syncResult = accountManagementService.syncAccountMovements(account, galiciaCookies);
                } else {
                    continue;
                }

                if (syncResult.isError() || syncResult.isWarning()) {
                    errors.add(account.getName() + ": " + syncResult.getMessage());
                } else {
                    syncResults.add((SyncResult) syncResult.getPayload());
                }
            } catch (Exception e) {
                log.warn("Error syncing account {}: {}", account.getName(), e.getMessage());
                errors.add(account.getName() + ": " + e.getMessage());
            }
        }

        for (SyncResult sr : syncResults) {
            if (sr.hasUnmatched()) {
                applicationMessageService.add(request, ApplicationMessage.warn(sr.toHtmlMessage()));
            } else {
                applicationMessageService.add(request, ApplicationMessage.success(sr.toHtmlMessage()));
            }
        }
        for (String error : errors) {
            applicationMessageService.add(request, ApplicationMessage.error(error));
        }

        return "redirect:/dashboard";
    }

    @RequestMapping(value = "/bank-pdf", method = RequestMethod.POST)
    public String postAnalizePdf(Model model,
                                 @RequestParam("accountName") Optional<String> accountName,
                                 @RequestParam("file") MultipartFile file,
                                 HttpServletRequest request) {
        final String backUrl = ApplicationUtils.getBackUrl(request, false, "/dashboard");

        Account account;
        if (accountName.isEmpty()) {
            applicationMessageService.add(request, ApplicationMessage.error("Account name is null"));
            return backUrl;
        } else {
            List<Long> accountSearchOwnerIds = new ArrayList<>();
            accountSearchOwnerIds.add(-1L); // La cuenta Generica la pueden utilizar todos los usuarios

            User user = ApplicationUtils.getUserFromSession(false);
            if (user != null) {
                // Si no tengo al usuario, no puedo ver ninguna cuenta mas que la -1
                accountSearchOwnerIds.add(user.getId());
            }

            AccountSearch accountSearch = new AccountSearch();
            accountSearch.setOwnerIds(accountSearchOwnerIds);
            accountSearch.setName(accountName.get());
            List<Account> userAccounts = accountRepository.findAll(specificationsService.getAccounts(accountSearch), Sort.by(Sort.Direction.ASC, "name"));
            if (CollectionUtils.isEmpty(userAccounts)) {
                applicationMessageService.add(request, ApplicationMessage.error("Invalid account: " + accountName.get()));
                return "redirect:" + backUrl;
            }
            account = userAccounts.get(0);
        }

        if (file == null) {
            applicationMessageService.add(request, ApplicationMessage.error("File is null"));
            return backUrl;
        }

        final String pdfAsText;
        try {
            pdfAsText = pdfService.extractText(file);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (!StringUtils.hasText(pdfAsText)) {
            applicationMessageService.add(request, ApplicationMessage.error("Pdf convertion returned null or empty text"));
            return backUrl;
        }

        Pair<Map<Expense, Expense>, List<Expense>> expensesAnalizedFromPdf;
        if (AccountType.CREDIT_CARD.equals(account.getType())){
            if ("Master Card".equals(account.getName())) {
                expensesAnalizedFromPdf = parseAndAnalyzeMasterCardPdf(account, Map.of(
                        file.getName(),
                        Arrays.stream(pdfAsText.split("\\R"))
                                .map(String::trim)
                                .filter(line -> !line.isEmpty())
                                .collect(Collectors.toList())
                ));
                // Si quiero leer los PDFs de un directorio
//                String folderPath = "";
//                try (Stream<Path> paths = Files.list(Path.of(folderPath))) {
//                    Map<String, List<String>> map = paths
//                            .filter(Files::isRegularFile)
//                            .filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
//                            .collect(Collectors.toMap(
//                                    p -> p.getFileName().toString(),
//                                    p -> {
//                                        try {
//                                            byte[] bytes = Files.readAllBytes(p);
//                                            return Arrays.stream(pdfService.extractText(bytes).split("\\R"))
//                                                    .map(String::trim)
//                                                    .filter(line -> !line.isEmpty())
//                                                    .collect(Collectors.toList());
//                                        } catch (IOException e) {
//                                            throw new UncheckedIOException(e);
//                                        }
//                                    },
//                                    (existing, replacement) -> existing,  // merge function
//                                    TreeMap::new                          // map supplier
//                            ));
//                    expensesAnalizedFromPdf = parseAndAnalyzeMasterCardPdf(account, map);
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
            } else if ("VISA".equals(account.getName())) {
                expensesAnalizedFromPdf = parseAndAnalyzeVisaPdf(account, pdfAsText);
            } else {
                applicationMessageService.add(request, ApplicationMessage.error("Invalid credit card: " + account.getName()));
                return backUrl;
            }
        } else {
            applicationMessageService.add(request, ApplicationMessage.error("Invalid account type: " + account.getType()));
            return backUrl;
        }
        List<Expense> expensesNotFounded = expensesAnalizedFromPdf.getSecond();

        ExpenseImportForm form = new ExpenseImportForm();
        List<ExpenseImportItem> items = expensesAnalizedFromPdf.getSecond()
                .stream()
                .map(e -> {
                    ExpenseImportItem item = new ExpenseImportItem();
                    item.setDate(e.getDate());
                    item.setDescription(e.getDescription());
                    item.setOriginalDescription(e.getOriginalDescription());
                    item.setDetails(e.getDetails());
                    item.setAmount(e.getAmount());
                    return item;
                })
                .collect(Collectors.toList());

        form.setExpenses(items);

        model.addAttribute("expenseImportForm", form);
        model.addAttribute("accountId", account.getId());
        model.addAttribute("currency", account.getCurrency() != null ? account.getCurrency() : "ARS");
        model.addAttribute("expensesFounded", expensesAnalizedFromPdf.getFirst());
        model.addAttribute("expensesNotFounded", expensesNotFounded);

        return "/report-expenses";
    }

    private Pair<Map<Expense, Expense>, List<Expense>> parseAndAnalyzeVisaPdf(Account account, String pdfVisaAsText) {
        LocalDate minDate = null, maxDate = null;
        final List<Expense> expensesFromPDF = new ArrayList<>();
        final List<String> pdfVisaLines = Arrays.stream(pdfVisaAsText.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());

        // Resuelvo los periodos del resumen. Los resumenes mas nuevos usan el layout con labels (CIERRE ACTUAL, PROXIMO CIERRE...)
        final List<CardPeriod> periodsFromPdf = resolveVisaPeriods(account, pdfVisaLines);
        cardPeriodService.syncPeriodsFromSummaries(account, periodsFromPdf);
        final CardPeriod currentPeriod = periodsFromPdf.isEmpty() ? null : periodsFromPdf.get(0);

        // Las cuotas se imputan al primer dia del periodo del resumen
        final LocalDate fixedQuotaDate = currentPeriod != null ? currentPeriod.getPeriodStart() : null;

        boolean startReading = false;
        for (String line : pdfVisaLines) {
            if (startReading) {
                Matcher matcher = VISA_EXPENSE_ROW_PATTERN.matcher(line);
                if (matcher.find()) {
                    String description = matcher.group(2).trim();

                    // descartar pagos en dólares
                    if (description.contains("USD")) continue;

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
                    expensesFromPDF.add(expenseFromPDF);

                    // La fecha minima no la quiero calcular a partir de los gastos de cuotas
                    if (minDate == null || date.isBefore(minDate)) minDate = date;
                    if (maxDate == null || date.isAfter(maxDate)) maxDate = date;
                }
            } else {
                startReading = line.startsWith("FECHA");
            }
        }

        return matchPdfExpensesWithAccount(expensesFromPDF, account, minDate, maxDate);
    }

    /**
     * Los resumenes con labels muestran el cierre actual, el vencimiento, el cierre anterior y los proximos cierre y vencimiento.
     * Cuando el vencimiento no entra en su linea (el label desborda la pagina) se toma de la linea siguiente.
     */
    private List<CardPeriod> resolveVisaPeriods(Account account, List<String> pdfVisaLines) {
        final List<CardPeriod> periodsFromSixDates = resolvePeriodsFromSixDates(account, pdfVisaLines);
        if (!periodsFromSixDates.isEmpty()) return periodsFromSixDates;

        LocalDate previousClosing = null, currentClosing = null, currentDue = null, nextClosing = null, nextDue = null;
        for (int i = 0; i < pdfVisaLines.size(); i++) {
            final String line = pdfVisaLines.get(i);

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
            if (currentDue == null && line.contains("VENCIMIENTO SALDO") && i + 1 < pdfVisaLines.size()) {
                final Matcher matcher = SUMMARY_DATE_IN_TEXT_PATTERN.matcher(pdfVisaLines.get(i + 1));
                if (matcher.find()) currentDue = parseSummaryDate(matcher.group(1));
            }
        }

        return buildSummaryPeriods(account, previousClosing, currentClosing, currentDue, nextClosing, nextDue);
    }

    /**
     * Los resumenes de Galicia traen 6 fechas correlativas: cierre del resumen anterior, su vencimiento,
     * cierre del resumen actual, su vencimiento, cierre del resumen siguiente y su vencimiento.
     */
    private List<CardPeriod> resolvePeriodsFromSixDates(Account account, List<String> pdfLines) {
        LocalDate[] dates = null;
        for (String line : pdfLines) {
            final Matcher matcher = SIX_DATES_PATTERN.matcher(line);
            if (!matcher.find()) continue;

            dates = new LocalDate[matcher.groupCount()];
            for (int i = 0; i < dates.length; i++) {
                dates[i] = parseSummaryDate(matcher.group(i + 1));
            }
            break;
        }
        if (dates == null || dates.length < 6) return List.of();

        return buildSummaryPeriods(account, dates[0], dates[2], dates[3], dates[4], dates[5]);
    }

    /**
     * Arma el periodo del resumen actual (cierre anterior + 1 dia .. cierre actual) y el del resumen siguiente,
     * para no tener que esperar al proximo PDF.
     */
    private List<CardPeriod> buildSummaryPeriods(Account account, LocalDate previousClosing, LocalDate currentClosing, LocalDate currentDue, LocalDate nextClosing, LocalDate nextDue) {
        if (currentClosing == null) return List.of();

        final List<CardPeriod> periods = new ArrayList<>();
        periods.add(buildSummaryPeriod(account, previousClosing != null ? previousClosing.plusDays(1) : null, currentClosing, currentDue));
        if (nextClosing != null) {
            periods.add(buildSummaryPeriod(account, currentClosing.plusDays(1), nextClosing, nextDue));
        }
        return periods;
    }

    private CardPeriod buildSummaryPeriod(Account account, LocalDate periodStart, LocalDate closingDate, LocalDate dueDate) {
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
    private LocalDate parseSummaryDate(String rawDate) {
        if (!StringUtils.hasText(rawDate)) return null;

        final Matcher matcher = SUMMARY_DATE_PATTERN.matcher(rawDate.replaceAll("[\\s.-]", ""));
        if (!matcher.matches()) return null;

        final String monthKey = matcher.group(2).substring(0, 1).toUpperCase(Locale.ROOT) + matcher.group(2).substring(1).toLowerCase(Locale.ROOT);
        final String month = DateUtils.MONTHS_ES.get(monthKey);
        if (month == null) return null;

        try {
            return LocalDate.parse(matcher.group(1) + "-" + month + "-" + matcher.group(3), SUMMARY_DATE_FORMATTER_EN);
        } catch (DateTimeParseException e) {
            return null;
        }
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

    private final static Pattern SIX_DATES_PATTERN = Pattern.compile(
            "(\\d{2}-[A-Za-z]{3}-\\d{2})\\s+" +
                    "(\\d{2}-[A-Za-z]{3}-\\d{2})\\s+" +
                    "(\\d{2}-[A-Za-z]{3}-\\d{2})\\s+" +
                    "(\\d{2}-[A-Za-z]{3}-\\d{2})\\s+" +
                    "(\\d{2}-[A-Za-z]{3}-\\d{2})\\s+" +
                    "(\\d{2}-[A-Za-z]{3}-\\d{2})"
    );
    private final static Pattern SUMMARY_DATE_PATTERN = Pattern.compile("(\\d{1,2})([A-Za-z]{3})(\\d{2})");
    private final static Pattern SUMMARY_DATE_IN_TEXT_PATTERN = Pattern.compile("(\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{2})");
    private final static Pattern VISA_CIERRE_ACTUAL_PATTERN = Pattern.compile("CIERRE ACTUAL:?\\s*(\\d{1,2}\\s*[A-Za-z]{3}\\s*\\d{2})");
    private final static Pattern VISA_PROXIMO_CIERRE_PATTERN = Pattern.compile("PROXIMO CIERRE:?\\s*(\\d{1,2}\\s*[A-Za-z]{3}\\s*\\d{2})");
    private final static Pattern VISA_PROXIMO_VTO_PATTERN = Pattern.compile("PROXIMO VTO\\.?:?\\s*(\\d{1,2}\\s*[A-Za-z]{3}\\s*\\d{2})");
    private final static DateTimeFormatter SUMMARY_DATE_FORMATTER_EN = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH);
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

    private final static Locale PDF_LOCALE = new Locale("es", "AR");
    private final static DateTimeFormatter FORMATTER_ARG = DateTimeFormatter.ofPattern("dd-MMM-yy", PDF_LOCALE);
    private final static DateTimeFormatter MASTERCARD_DATE_FORMATTER_EN = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH);

    private Pair<Map<Expense, Expense>, List<Expense>> parseAndAnalyzeMasterCardPdf(Account account, Map<String, List<String>> masterCardPdfsWithLines) {
        LocalDate minDate = null, maxDate = null;
        final List<Expense> expensesFromPDFs = new ArrayList<>();
        final List<CardPeriod> periodsFromPDFs = new ArrayList<>();
        for (String pdfName : masterCardPdfsWithLines.keySet()) {
            final List<Expense> expensesFromPDF = new ArrayList<>();
            final List<String> pdfMasterCardLines = masterCardPdfsWithLines.get(pdfName);

            // Resuelvo el periodo del resumen a partir de las 6 fechas del encabezado
            final List<CardPeriod> periodsFromPdf = resolvePeriodsFromSixDates(account, pdfMasterCardLines);
            periodsFromPDFs.addAll(periodsFromPdf);
            final LocalDate periodStart = periodsFromPdf.isEmpty() ? null : periodsFromPdf.get(0).getPeriodStart();
            // Los DEV/PERCEP no tienen fecha propia, se imputan al cierre del resumen anterior
            final LocalDate previousClosing = periodStart != null ? periodStart.minusDays(1) : null;
            final LocalDate fixedQuotaDate = periodStart;

            boolean areCuotas = false;
            boolean isConsumptionSection = false;
            for (String line : pdfMasterCardLines) {

                if (isConsumptionSection) {
                    // Si llegue a la linea que dice "Cuotas a vencer", entonces ya termine de leer los consumos del resumen
                    if (line.startsWith("Cuotas a vencer")) break;

                    Matcher matcher = CONSUMPTION_PATTERN.matcher(line);
                    if (matcher.find()) {
                        String description = matcher.group(2).trim();

                        // descartar pagos en dólares
                        if (description.contains("U$S") || description.contains("USD") || description.contains("CLP")) continue;

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
                            if (fixedQuotaDate != null && cuotaActual > 1) expenseFromPDF.setDate(fixedQuotaDate);
                        }
                        expensesFromPDF.add(expenseFromPDF);

                        // La fecha minima no la quiero calcular a partir de los gastos de cuotas
                        if (!areCuotas) if (minDate == null || date.isBefore(minDate)) minDate = date;
                        if (maxDate == null || date.isAfter(maxDate)) maxDate = date;
                    } else {
                        matcher = DEV_PERCEP_PATTERN.matcher(line);
                        if (matcher.find()) {
                            String description = matcher.group(1).trim();
                            String amount = matcher.group(2)
                                    .replace(".", "")
                                    .replace(",", ".");

                            Expense expenseFromPDF = new Expense();
                            if (previousClosing != null) expenseFromPDF.setDate(previousClosing);
                            expenseFromPDF.setDescription(description);
                            expenseFromPDF.setOriginalDescription(description);
                            expenseFromPDF.setAmount(new BigDecimal(amount));

                            expensesFromPDF.add(expenseFromPDF);
                        } else if (line.equals("CUOTA DEL MES")) {
                            areCuotas = true;
                        }
                    }
                } else {
                    isConsumptionSection = line.startsWith("CONSOLIDADO");
                }
            }

            log.debug("{}[fechaCierre: {}, consumos: {}, cantLineas: {}]", pdfName, previousClosing != null ? previousClosing.format(FORMATTER_ARG) : "N/A", expensesFromPDF.size(), pdfMasterCardLines.size());
            expensesFromPDFs.addAll(expensesFromPDF);
        }

        log.debug("Luego de procesar {} obtuve como rango de fechas: {} al {}", masterCardPdfsWithLines.size() + " PDF" + (masterCardPdfsWithLines.size() > 1 ? "s" : ""), DateUtils.format(minDate), DateUtils.format(maxDate));

        cardPeriodService.syncPeriodsFromSummaries(account, periodsFromPDFs);

        return matchPdfExpensesWithAccount(expensesFromPDFs, account, minDate, maxDate);
    }

    /**
     * Devuelve del lado izq un Map donde la key es un Expense de la DB asociado a todos los Expense creados dinamicamente a partir de las lineas del PDF
     * Y del lado der el listado de Expenses creados dinamicamente a partir de las lineas del PDF que NO fueron encontradas en la DB
     */
    private Pair<Map<Expense, Expense>, List<Expense>> matchPdfExpensesWithAccount(List<Expense> expensesFromPDF, Account account, LocalDate dateFrom, LocalDate dateTo) {
        final Map<Expense, Expense> expensesFounded = new HashMap<>();
        final List<Expense> expensesNotFounded = new ArrayList<>();

        // Si no pude leer ningun gasto del PDF, no tiene sentido seguir
        if (CollectionUtils.isEmpty(expensesFromPDF)) {
            return Pair.of(expensesFounded, expensesNotFounded);
        }

        final List<Expense> allExpenses = expenseRepository.findByAccountAndDateBetween(account, dateFrom, dateTo, Sort.by(Sort.Direction.DESC, "date", "id"));
        // IDs DB ya usados para evitar doble match
        final Set<Long> matchedExpenseIds = new HashSet<>();

        for (Expense expenseFromPDF : expensesFromPDF) {
            Optional<Expense> match = allExpenses.stream()
                    .filter(expense -> {
                        // Ya usado por otro PDF expense
                        if (matchedExpenseIds.contains(expense.getId())) {
                            return false;
                        }

                        // Distinto importe
                        if (expenseFromPDF.getAmount().compareTo(expense.getAmount()) != 0) {
                            return false;
                        }

                        if (expenseFromPDF.getDetails() != null) {
                            // Es un gasto en cuotas, si coincide el nro de cuota y el total, lo tomo como valido
                            return expense.getDetails() != null && expense.getDetails().contains(expenseFromPDF.getDetails());
                        }

                        // Si el expenseFromPDF es un "DEV PER RG 4815 30% -31.275,23", no tiene date
                        if (expenseFromPDF.getDescription() != null && (expenseFromPDF.getDescription().startsWith("DEV") || expenseFromPDF.getDescription().startsWith("PERC"))) {
                            return true;
                        }

                        // Es un gasto normal, descarto si no coincide la fecha
                        return expenseFromPDF.getDate() != null && expenseFromPDF.getDate().equals(expense.getDate());
                    })
                    .findFirst();

            if (match.isPresent()) {
                Expense matchedExpense = match.get();
                matchedExpenseIds.add(matchedExpense.getId());
                expensesFounded.put(matchedExpense, expenseFromPDF);
            } else {
                expensesNotFounded.add(expenseFromPDF);
            }
        }

        // Gastos DB que no aparecieron en PDF
        List<Expense> unmatchedDB = allExpenses.stream()
                .filter(expense -> !matchedExpenseIds.contains(expense.getId()))
                .sorted(Comparator.comparing(Expense::getDate).reversed())
                .collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(unmatchedDB)) {
            log.info("Los siguientes gastos fueron encontrados en DB pero no en el PDF:");
            unmatchedDB.forEach(expense -> log.info(expense.toDebugString()));
        }

        return Pair.of(expensesFounded, expensesNotFounded);
    }

    @PostMapping("/bank-pdf/save-expenses")
    public String saveExpenses(@ModelAttribute ExpenseImportForm expenseImportForm,
                               @RequestParam Long accountId) {

        Account account = accountRepository.findById(accountId).orElseThrow();
        User user = ApplicationUtils.getUserFromSession();

        for (ExpenseImportItem item : expenseImportForm.getExpenses()) {
            if (item.isSelected()) {
                Expense expense = new Expense();
                expense.setDate(item.getDate());
                expense.setDescription(item.getDescription());
                expense.setOriginalDescription(item.getOriginalDescription());
                expense.setDetails(item.getDetails());
                expense.setAmount(item.getAmount());
                expense.setAccount(account);
                expense.setTags(new ArrayList<>());
                expense.setUser(user);

                expenseService.saveWithAudit(expense, user);
            }
        }

        return "redirect:/expenses";
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(
                Date.class,
                new CustomDateEditor(new SimpleDateFormat("dd/MM/yyyy"), true)
        );
    }
}
