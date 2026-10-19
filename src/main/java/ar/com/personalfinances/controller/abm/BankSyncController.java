package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.service.*;
import ar.com.personalfinances.service.creditcard.CreditCardStatement;
import ar.com.personalfinances.service.creditcard.CreditCardStatementFormat;
import ar.com.personalfinances.service.creditcard.CreditCardStatementResolver;
import ar.com.personalfinances.service.creditcard.CreditCardStatementResult;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
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
    private final CreditCardStatementResolver creditCardStatementResolver;

    public BankSyncController(SpecificationsService specificationsService, AccountRepository accountRepository, ApplicationMessageService applicationMessageService, AccountManagementService accountManagementService, PDFService pdfService, ExpenseRepository expenseRepository, ExpenseService expenseService, CardPeriodService cardPeriodService, CreditCardStatementResolver creditCardStatementResolver) {
        this.specificationsService = specificationsService;
        this.accountRepository = accountRepository;
        this.applicationMessageService = applicationMessageService;
        this.accountManagementService = accountManagementService;
        this.pdfService = pdfService;
        this.expenseRepository = expenseRepository;
        this.expenseService = expenseService;
        this.cardPeriodService = cardPeriodService;
        this.creditCardStatementResolver = creditCardStatementResolver;
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
                List<Account> userAccounts = accountRepository.findAll(specificationsService.getAccounts(accountSearch), Sort.by(Sort.Direction.ASC, "name"));
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
                                 @RequestParam(value = "pathToPdfs", defaultValue = "D:\\Javi\\Credit cards statements\\Master Card") Optional<String> pathToPdfs,
                                 @RequestParam(value = "visaFormat", required = false, defaultValue = "VISA_GALICIA") CreditCardStatementFormat visaFormat,
                                 HttpServletRequest request) {
        final String backUrl = ApplicationUtils.getBackUrl(request, false, "/dashboard");

        if (accountName.isEmpty()) {
            applicationMessageService.add(request, ApplicationMessage.error("Account name is null"));
            return "redirect:" + backUrl;
        }

        if (file == null) {
            applicationMessageService.add(request, ApplicationMessage.error("File is null"));
            return "redirect:" + backUrl;
        }

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
        final Account account = userAccounts.getFirst();

        if (!AccountType.CREDIT_CARD.equals(account.getType())) {
            applicationMessageService.add(request, ApplicationMessage.error("Invalid account type: " + account.getType()));
            return "redirect:" + backUrl;
        }

        final CreditCardStatementFormat format;
        if ("Master Card".equals(account.getName())) {
            format = CreditCardStatementFormat.MASTER_CARD_GALICIA;
        } else if ("VISA".equals(account.getName())) {
            format = visaFormat;
        } else {
            applicationMessageService.add(request, ApplicationMessage.error("Invalid credit card: " + account.getName()));
            return "redirect:" + backUrl;
        }

        Map<String, String> linesByPdf;
        if (pathToPdfs.isPresent()) {
            try (Stream<Path> paths = Files.list(Path.of(pathToPdfs.get()))) {
                linesByPdf = paths
                        .filter(Files::isRegularFile)
                        .filter(fileName -> fileName.toString().toLowerCase().endsWith(".pdf"))
                        .collect(Collectors.toMap(
                                pdf -> pdf.getFileName().toString(),
                                pdf -> {
                                    try {
                                        byte[] bytes = Files.readAllBytes(pdf);
                                        return pdfService.extractText(bytes);
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                },
                                (existing, replacement) -> existing,
                                TreeMap::new
                        ));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            final String pdfAsText;
            try {
                pdfAsText = pdfService.extractText(file);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (!StringUtils.hasText(pdfAsText)) {
                applicationMessageService.add(request, ApplicationMessage.error("Pdf convertion returned null or empty text"));
                return "redirect:" + backUrl;
            }

            linesByPdf = Map.of(Objects.requireNonNullElse(file.getOriginalFilename(), file.getName()), pdfAsText);
        }

        Map<String, List<String>> linesByFileName = new LinkedHashMap<>();
        for (String pdfFileName : linesByPdf.keySet()) {
            String pdfLines = linesByPdf.get(pdfFileName);
            linesByFileName.put(
                    pdfFileName,
                    Arrays.stream(pdfLines.split("\\R"))
                            .map(String::trim)
                            .filter(line -> !line.isEmpty())
                            .collect(Collectors.toList()));
        }

        final CreditCardStatement statement = creditCardStatementResolver.resolve(format);
        final Map<CardPeriod, List<Expense>> expensesByPeriod = new LinkedHashMap<>();
        for (String pdfFileName : linesByFileName.keySet()) {
            final CreditCardStatementResult result = statement.parse(account, pdfFileName, linesByFileName.get(pdfFileName));
            expensesByPeriod.computeIfAbsent(result.getPeriod(), k -> new ArrayList<>()).addAll(result.getExpenses());
        }

        Pair<Map<Expense, Expense>, List<Expense>> expensesAnalizedFromPdf = matchPdfExpensesWithAccount(expensesByPeriod, account);
        ExpenseImportForm form = new ExpenseImportForm();
        List<ExpenseImportItem> items = expensesAnalizedFromPdf.getSecond()
                .stream()
                .sorted(Comparator
                        .comparing((Expense e) -> e.getPeriod() != null ? e.getPeriod().getClosingDate() : null, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Expense::getDate))
                .map(e -> {
                    ExpenseImportItem item = new ExpenseImportItem();
                    item.setDate(e.getDate());
                    item.setDescription(e.getDescription());
                    item.setOriginalDescription(e.getOriginalDescription());
                    item.setDetails(e.getDetails());
                    item.setAmount(e.getAmount());
                    if (e.getPeriod() != null) {
                        item.setPeriodId(e.getPeriod().getId());
                        item.setPeriodClosingDate(e.getPeriod().getClosingDate());
                        item.setPeriodMonthLabel(e.getPeriod().getPeriodMonthLabel());
                    }
                    return item;
                })
                .collect(Collectors.toList());

        form.setExpenses(items);

        model.addAttribute("expenseImportForm", form);
        model.addAttribute("accountId", account.getId());
        model.addAttribute("currency", account.getCurrency() != null ? account.getCurrency() : "ARS");
        model.addAttribute("expensesFounded", expensesAnalizedFromPdf.getFirst());
        model.addAttribute("expensesNotFounded", items);

        return "/report-expenses";
    }

    /**
     * Empareja los gastos de cada periodo contra los gastos de la DB de ese mismo periodo.
     * Devuelve del lado izq un Map donde la key es un Expense de la DB asociado a todos los Expense creados dinamicamente a partir de las lineas del PDF
     * Y del lado der el listado de Expenses creados dinamicamente a partir de las lineas del PDF que NO fueron encontradas en la DB
     */
    private Pair<Map<Expense, Expense>, List<Expense>> matchPdfExpensesWithAccount(Map<CardPeriod, List<Expense>> expensesByPeriod, Account account) {
        final Map<Expense, Expense> expensesFounded = new HashMap<>();
        final List<Expense> expensesNotFounded = new ArrayList<>();

        if (CollectionUtils.isEmpty(expensesByPeriod)) {
            return Pair.of(expensesFounded, expensesNotFounded);
        }

        // Los gastos que todavia no tienen periodo pueden pertenecer a cualquiera de los resumenes que se estan
        // procesando. No se filtran por rango: una cuota tiene fecha de compra, y esa fecha es anterior al
        // resumen donde se cobra, asi que un filtro por periodo la dejaria sin poder emparejarse.
        final List<Expense> expensesWithoutPeriod = expenseRepository.findByAccountAndPeriodIsNull(account);

        // IDs DB ya usados para evitar doble match
        final Set<Long> matchedExpenseIds = new HashSet<>();
        // Candidatos consultados en DB, para despues reportar los que no quedaron emparejados
        final Map<Long, Expense> dbCandidates = new LinkedHashMap<>();

        for (Map.Entry<CardPeriod, List<Expense>> entry : expensesByPeriod.entrySet()) {
            final List<Expense> candidates = new ArrayList<>(expenseRepository.findByAccountAndPeriod(account, entry.getKey()));
            candidates.addAll(expensesWithoutPeriod);
            candidates.sort(Comparator.comparing(Expense::getDate, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Expense::getId));

            candidates.forEach(candidate -> dbCandidates.put(candidate.getId(), candidate));

            for (Expense expenseFromPDF : entry.getValue()) {
                Optional<Expense> match = candidates.stream()
                        .filter(expense -> !matchedExpenseIds.contains(expense.getId()))
                        .filter(expense -> expenseFromPDF.getAmount().compareTo(expense.getAmount()) == 0)
                        .filter(expense -> matchPdfExpense(expenseFromPDF, expense))
                        .findFirst();

                if (match.isPresent()) {
                    Expense matchedExpense = match.get();
                    matchedExpenseIds.add(matchedExpense.getId());
                    expensesFounded.put(matchedExpense, expenseFromPDF);
                } else {
                    expensesNotFounded.add(expenseFromPDF);
                }
            }
        }

        // Gastos DB que no aparecieron en PDF. Los que quedaron sin periodo y estan fuera del rango de los
        // periodos procesados se omiten del reporte: no es informacion sobre estos resumenes.
        final LocalDate rangeFrom = expensesByPeriod.keySet().stream().map(CardPeriod::getPeriodStart).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        final LocalDate rangeTo = expensesByPeriod.keySet().stream().map(CardPeriod::getClosingDate).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);

        List<Expense> unmatchedDB = dbCandidates.values().stream()
                .filter(expense -> !matchedExpenseIds.contains(expense.getId()))
                .filter(expense -> expense.getPeriod() != null
                        || rangeFrom == null || rangeTo == null
                        || (expense.getDate() != null && !expense.getDate().isBefore(rangeFrom) && !expense.getDate().isAfter(rangeTo)))
                .sorted(Comparator.comparing(Expense::getDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(unmatchedDB)) {
            log.info("Los siguientes gastos fueron encontrados en DB pero no en el PDF:");
            unmatchedDB.forEach(expense -> log.info(expense.toDebugString()));
        }

        return Pair.of(expensesFounded, expensesNotFounded);
    }

    private boolean matchPdfExpense(Expense expenseFromPDF, Expense expense) {
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
                if (item.getPeriodId() != null) {
                    cardPeriodService.findById(item.getPeriodId()).ifPresent(expense::setPeriod);
                }

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
