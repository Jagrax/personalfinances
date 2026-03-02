package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.CategoryRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.service.*;
import ar.com.personalfinances.util.*;
import ar.com.personalfinances.web.form.ExpenseImportForm;
import ar.com.personalfinances.web.form.ExpenseImportItem;
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

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final CategoryRepository categoryRepository;

    public BankSyncController(SpecificationsService specificationsService, AccountRepository accountRepository, ApplicationMessageService applicationMessageService, AccountManagementService accountManagementService, PDFService pdfService, ExpenseRepository expenseRepository, ExpenseService expenseService, CategoryRepository categoryRepository) {
        this.specificationsService = specificationsService;
        this.accountRepository = accountRepository;
        this.applicationMessageService = applicationMessageService;
        this.accountManagementService = accountManagementService;
        this.pdfService = pdfService;
        this.expenseRepository = expenseRepository;
        this.expenseService = expenseService;
        this.categoryRepository = categoryRepository;
    }

    @RequestMapping(value = "/bank-sync", method = RequestMethod.GET)
    public String getBankSyncPage(Model model) {
        model.addAttribute("bankSyncModelAttribute", new BankSyncModelAttribute());

        List<Long> accountSearchOwnerIds = new ArrayList<>();
        accountSearchOwnerIds.add(-1L); // La cuenta Generica la pueden utilizar todos los usuarios

        User user = ApplicationUtils.getUserFromSession(false);
        if (user != null) {
            // Si no tengo al usuario, no puedo ver ninguna cuenta mas que la -1
            accountSearchOwnerIds.add(user.getId());
        }

        AccountSearch accountSearch = new AccountSearch();
        accountSearch.setOwnerIds(accountSearchOwnerIds);
        model.addAttribute("accounts", accountRepository.findAll(specificationsService.getAccounts(accountSearch), Sort.by(Sort.Direction.ASC,"name")));

        // Atributo usado para settear la clase 'active' en el item del menu que corresponda
        model.addAttribute("module", "expenses");

        return "abm/bank-sync";
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

            if (!StringUtils.hasText(bankSyncModelAttribute.getAspNetSessionId())) {
                applicationMessageService.add(request, ApplicationMessage.error("ASP.NET_SessionId is null"));
                return "redirect:" + backUrl;
            }

            CommonResult learnFromBankMovementsResult = accountManagementService.learnFromBankMovements(account, bankSyncModelAttribute.getAspNetSessionId());
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

            if (!StringUtils.hasText(bankSyncModelAttribute.getAspNetSessionId()) && account.getType().equals(AccountType.BANK_ACCOUNT)) {
                applicationMessageService.add(request, ApplicationMessage.error("Cookie null"));
                return "redirect:" + backUrl;
            }

            switch (account.getType()) {
                case CREDIT_CARD: {
                    CommonResult syncResult = accountManagementService.syncCreditCardAccountMovements(account);
                    if (syncResult.isError() || syncResult.isWarning()) {
                        applicationMessageService.add(request, ApplicationMessage.error(syncResult.getMessage()));
                        return "redirect:" + backUrl;
                    } else {
                        applicationMessageService.add(request, ApplicationMessage.success(syncResult.getMessage()));
                    }
                    break;
                }
                case BANK_ACCOUNT: {
                    CommonResult syncAccountMovementsResult = accountManagementService.syncAccountMovements(account, bankSyncModelAttribute.getAspNetSessionId());
                    if (syncAccountMovementsResult.isError() || syncAccountMovementsResult.isWarning()) {
                        applicationMessageService.add(request, ApplicationMessage.error(syncAccountMovementsResult.getMessage()));
                        return "redirect:" + backUrl;
                    } else {
                        applicationMessageService.add(request, ApplicationMessage.success(syncAccountMovementsResult.getMessage()));
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

        Pair<Map<Expense, List<Expense>>, List<Expense>> expensesAnalizedFromPdf;
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

    private Pair<Map<Expense, List<Expense>>, List<Expense>> parseAndAnalyzeVisaPdf(Account account, String pdfVisaAsText) {
        final Pattern expenseRowPattern = Pattern.compile(
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

        final String datePattern = "dd.MM.yy";
        final DateTimeFormatter formatterEs = DateTimeFormatter.ofPattern(datePattern, new Locale("es"));
        final DateTimeFormatter formatterEn = DateTimeFormatter.ofPattern(datePattern, Locale.ENGLISH);
        boolean startReading = false;
        LocalDate minDate = null, maxDate = null, fixedQuotaDate = null;
        List<Expense> expensesFromPDF = new ArrayList<>();
        for (String textRow : pdfVisaAsText.split("\n")) {
            if (textRow == null) continue;
            String row = textRow.trim();              // quita espacios alrededor
            if (row.isEmpty()) continue;

            if (row.startsWith("CIERRE ANTERIOR")) {
                final String cierreAnteriorRaw = row.substring("CIERRE ANTERIOR".length(), row.indexOf("PAGO MIN. ANT."));
                if (StringUtils.hasText(cierreAnteriorRaw)) {
                    final String cierreAnteriorClened = cierreAnteriorRaw.replaceAll("\\s+", "");
                    final Matcher m = Pattern.compile("(\\d{1,2})([A-Za-z]{3})(\\d{2})").matcher(cierreAnteriorClened);
                    if (m.matches()) {
                        String monthStr = m.group(2).toLowerCase(Locale.ROOT);
                        int month;
                        switch (monthStr) {
                            case "ene": month = Calendar.JANUARY; break;
                            case "feb": month = Calendar.FEBRUARY; break;
                            case "mar": month = Calendar.MARCH; break;
                            case "abr": month = Calendar.APRIL; break;
                            case "may": month = Calendar.MAY; break;
                            case "jun": month = Calendar.JUNE; break;
                            case "jul": month = Calendar.JULY; break;
                            case "ago": month = Calendar.AUGUST; break;
                            case "sep": month = Calendar.SEPTEMBER; break;
                            case "oct": month = Calendar.OCTOBER; break;
                            case "nov": month = Calendar.NOVEMBER; break;
                            case "dic": month = Calendar.DECEMBER; break;
                            default:
                                throw new IllegalArgumentException("Mes inválido: " + monthStr);
                        }

                        fixedQuotaDate = LocalDate.of(2000 + Integer.parseInt(m.group(3)), month + 1, Integer.parseInt(m.group(1)))
                                // Le sumo 1 dia para que simule el 1er dia del periodo actual
                                .plusDays(1);
                    }
                }
            }

            if (startReading) {
                Matcher matcher = expenseRowPattern.matcher(row);
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
                        date = LocalDate.parse(matcher.group(1), formatterEs);
                    } catch (DateTimeParseException e) {
                        try {
                            date = LocalDate.parse(matcher.group(1), formatterEn);
                        } catch (DateTimeParseException e2) {
                            throw new IllegalArgumentException("Fecha inválida: " + matcher.group(1));
                        }
                    }

                    Expense expenseFromPDF = new Expense();
                    expenseFromPDF.setDate(date);
                    expenseFromPDF.setDescription(description);
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
                startReading = row.startsWith("FECHA");
            }
        }

        return matchPdfExpensesWithAccount(expensesFromPDF, account, minDate, maxDate);
    }

    private final static Pattern FECHA_CIERRE_PATTERN = Pattern.compile("vigente al (\\d{2}/\\d{2}/\\d{4})");
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
                    + "((?:DEV|PERCEP\\.AFIP).+?)"   // 1 descripción
                    + "\\s+"
                    + "(-?[0-9.]+,[0-9]{2})"         // 2 importe
                    + "$"
    );

    private final static DateTimeFormatter FORMATTER_ARG = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Pair<Map<Expense, List<Expense>>, List<Expense>> parseAndAnalyzeMasterCardPdf(Account account, Map<String, List<String>> masterCardPdfsWithLines) {
        final String datePattern = "dd-MMM-yy";
        final DateTimeFormatter formatterEs = DateTimeFormatter.ofPattern(datePattern, new Locale("es"));
        final DateTimeFormatter formatterEn = DateTimeFormatter.ofPattern(datePattern, Locale.ENGLISH);
        LocalDate minDate = null, maxDate = null;
        final List<Expense> expensesFromPDFs = new ArrayList<>();
        for (String pdfName : masterCardPdfsWithLines.keySet()) {
            final List<Expense> expensesFromPDF = new ArrayList<>();
            final List<String> pdfMasterCardLines = masterCardPdfsWithLines.get(pdfName);

            // Resuelvo la fecha de cierre
            final LocalDate fechaCierre = pdfMasterCardLines.stream()
                    .filter(StringUtils::hasText)
                    .map(FECHA_CIERRE_PATTERN::matcher)
                    .filter(Matcher::find)
                    .map(matcher -> LocalDate.parse(matcher.group(1), FORMATTER_ARG))
                    .findFirst().orElse(null);

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
                        if (description.contains("U$S")) continue;

                        String amount = matcher.group(6).replace(".", "").replace(",", ".");         // 13.600,00
                        LocalDate date;
                        try {
                            date = LocalDate.parse(matcher.group(1), formatterEs);
                        } catch (DateTimeParseException e) {
                            try {
                                date = LocalDate.parse(matcher.group(1), formatterEn);
                            } catch (DateTimeParseException e2) {
                                throw new IllegalArgumentException("Fecha inválida: " + matcher.group(1));
                            }
                        }

                        Expense expenseFromPDF = new Expense();
                        expenseFromPDF.setDate(date);
                        expenseFromPDF.setDescription(description);
                        expenseFromPDF.setAmount(new BigDecimal(amount));
                        String quotaNum = matcher.group(3);
                        String quotaDen = matcher.group(4);
                        if (quotaNum != null && quotaDen != null) {
                            expenseFromPDF.setDetails("Cuota " + Integer.parseInt(quotaNum) + " de " + Integer.parseInt(quotaDen));
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
                            if (fechaCierre != null) expenseFromPDF.setDate(fechaCierre);
                            expenseFromPDF.setDescription(description);
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

            log.debug("{}[fechaCierre: {}, consumos: {}, cantLineas: {}]", pdfName, fechaCierre != null ? fechaCierre.format(FORMATTER_ARG) : "N/A", expensesFromPDF.size(), pdfMasterCardLines.size());
            expensesFromPDFs.addAll(expensesFromPDF);
        }

        log.debug("Luego de procesar {} obtuve como rango de fechas: {} al {}", masterCardPdfsWithLines.size() + " PDF" + (masterCardPdfsWithLines.size() > 1 ? "s" : ""), DateUtils.format(minDate), DateUtils.format(maxDate));

        return matchPdfExpensesWithAccount(expensesFromPDFs, account, minDate, maxDate);
    }

    /**
     * Devuelve del lado izq un Map donde la key es un Expense de la DB asociado a todos los Expense creados dinamicamente a partir de las lineas del PDF
     * Y del lado der el listado de Expenses creados dinamicamente a partir de las lineas del PDF que NO fueron encontradas en la DB
     */
    private Pair<Map<Expense, List<Expense>>, List<Expense>> matchPdfExpensesWithAccount(List<Expense> expensesFromPDF, Account account, LocalDate dateFrom, LocalDate dateTo) {
        final Map<Expense, List<Expense>> expensesFounded = new HashMap<>();
        final List<Expense> expensesNotFounded = new ArrayList<>();

        // Si no pude leer ningun gasto del PDF, no tiene sentido seguir
        if (CollectionUtils.isEmpty(expensesFromPDF)) {
            return Pair.of(expensesFounded, expensesNotFounded);
        }

        final List<Expense> allExpenses = expenseRepository.findByAccountAndDateBetween(account, dateFrom, dateTo, Sort.by(Sort.Direction.DESC, "date", "id"));
        final List<Expense> expensesFromPDFMatched = new ArrayList<>();
        for (Expense expense : allExpenses) {
            Optional<Expense> match = expensesFromPDF.stream()
                    .filter(expenseFromPDF -> {
                        // Si ya fue usada para otro gasto
                        if (expensesFromPDFMatched.contains(expenseFromPDF)) {
                            return false;
                        }

                        // Distinto importe
                        if (!expenseFromPDF.getAmount().equals(expense.getAmount())) {
                            return false;
                        }

                        // Es un gasto normal, descarto si no coincide la fecha
                        if (expenseFromPDF.getDetails() != null) {
                            // Es un gasto en cuotas, si coincide el nro de cuota y el total, lo tomo como valido
                            return expense.getDetails() != null && expense.getDetails().contains(expenseFromPDF.getDetails());
                        } else {
                            // Si el expenseFromPDF es un "DEV PER RG 4815 30% -31.275,23", no tiene date
                            return (expenseFromPDF.getDate() != null && expenseFromPDF.getDate().equals(expense.getDate())) || expenseFromPDF.getDescription().startsWith("DEV");
                        }
                    })
                    .findFirst();

            if (match.isPresent()) {
                expensesFounded.put(expense, List.of(match.get()));
                expensesFromPDFMatched.add(match.get());
            } else {
                expensesFounded.put(expense, Collections.emptyList());
            }
        }

        // Estos son los gastos del PDF que no encontre en la DB
        expensesNotFounded.addAll(expensesFromPDF.stream()
                .filter(expenseFromPDF -> !expensesFromPDFMatched.contains(expenseFromPDF))
                .collect(Collectors.toList())
        );

        // Y estos son los de la DB que no encontre en el PDF
        List<Expense> unmatchedDB = expensesFounded.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(unmatchedDB)) {
            log.info("Los siguientes gastos fueron encontrados en el rango de fechas de los gastos del PDF, pero no fueron hayados en el PDF en si:");
            unmatchedDB.forEach(expense -> log.info(expense.toDebugString()));
        }

        return Pair.of(expensesFounded, expensesNotFounded);
    }

    @PostMapping("/bank-pdf/save-expenses")
    public String saveExpenses(@ModelAttribute ExpenseImportForm expenseImportForm,
                               @RequestParam Long accountId) {

        Account account = accountRepository.findById(accountId).orElseThrow();
        User user = ApplicationUtils.getUserFromSession();
        final Category automaticCategory = categoryRepository.findById(Category.AUTOMATIC_CATEGORY_ID).orElseThrow(() -> new ResourceNotFoundException("Category", "id", Category.AUTOMATIC_CATEGORY_ID));

        for (ExpenseImportItem item : expenseImportForm.getExpenses()) {
            if (item.isSelected()) {
                Expense expense = new Expense();
                expense.setDate(item.getDate());
                expense.setDescription(item.getDescription());
                expense.setAmount(item.getAmount());
                expense.setAccount(account);
                expense.setCategory(automaticCategory);
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