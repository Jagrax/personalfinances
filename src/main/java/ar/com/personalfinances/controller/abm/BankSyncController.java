package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.AccountType;
import ar.com.personalfinances.entity.Expense;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.service.AccountManagementService;
import ar.com.personalfinances.service.ApplicationMessageService;
import ar.com.personalfinances.service.PDFService;
import ar.com.personalfinances.service.SpecificationsService;
import ar.com.personalfinances.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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

    public BankSyncController(SpecificationsService specificationsService, AccountRepository accountRepository, ApplicationMessageService applicationMessageService, AccountManagementService accountManagementService, PDFService pdfService, ExpenseRepository expenseRepository) {
        this.specificationsService = specificationsService;
        this.accountRepository = accountRepository;
        this.applicationMessageService = applicationMessageService;
        this.accountManagementService = accountManagementService;
        this.pdfService = pdfService;
        this.expenseRepository = expenseRepository;
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

        Pair<Map<Expense, List<Expense>>, List<Expense>> expensesAnalizedFromPdf = analizePdfMasterCard(account, pdfAsText);
        model.addAttribute("expensesFounded", expensesAnalizedFromPdf.getFirst());
        model.addAttribute("expensesNotFounded", expensesAnalizedFromPdf.getSecond());

        return "/report-expenses";
    }

    /**
     * Devuelve del lado izq un Map donde la key es un Expense de la DB asociado a todos los Expense creados dinamicamente a partir de las lineas del PDF
     * Y del lado der el listado de Expenses creados dinamicamente a partir de las lineas del PDF que NO fueron encontradas en la DB
     */
    private Pair<Map<Expense, List<Expense>>, List<Expense>> analizePdfMasterCard(Account account, String pdfMasterCardAsText) {
        final Pattern expenseRowPattern = Pattern.compile(
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

        final String datePattern = "dd-MMM-yy";
        SimpleDateFormat sdf = new SimpleDateFormat(datePattern, new Locale("es"));
        boolean startReading = false;
        boolean areCuotas = false;
        Date minDate = null, maxDate = null;
        List<Expense> expensesFromPDF = new ArrayList<>();
        for (String textRow : pdfMasterCardAsText.split("\n")) {
            if (textRow == null) continue;
            String row = textRow.trim();              // quita espacios alrededor
            if (row.isEmpty()) continue;

            if (startReading) {
                // TODO: Discriminar si estoy leyendo la seccion CONSOLIDADO o COMPRAS DEL MES
                Matcher matcher = expenseRowPattern.matcher(row);
                if (matcher.find()) {
                    String description = matcher.group(2).trim();

                    // descartar pagos en dólares
                    if (description.contains("U$S")) continue;

                    String amount = matcher.group(6).replace(".", "").replace(",", ".");         // 13.600,00
                    Date date;
                    try {
                        date = sdf.parse(matcher.group(1));
                    } catch (ParseException e) {
                        try {
                            date = new SimpleDateFormat(datePattern, Locale.ENGLISH).parse(matcher.group(1));
                        } catch (ParseException e2) {
                            throw new IllegalArgumentException("Fecha inválida: " + e2);
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
                    if (!areCuotas) {
                        if (minDate == null) {
                            minDate = date;
                        } else if (date.before(minDate)) {
                            minDate = date;
                        }
                    }

                    if (maxDate == null) {
                        maxDate = date;
                    } else if (date.after(maxDate)) {
                        maxDate = date;
                    }
                } else if (row.equals("CUOTA DEL MES")) {
                    areCuotas = true;
                }
            } else {
                startReading = row.startsWith("CONSOLIDADO");
            }
        }

        final Map<Expense, List<Expense>> expensesFounded = new HashMap<>();
        final List<Expense> expensesNotFounded = new ArrayList<>();

        // Si no pude leer ningun gasto del PDF, no tiene sentido seguir
        if (CollectionUtils.isEmpty(expensesFromPDF)) {
            return Pair.of(expensesFounded, expensesNotFounded);
        }

        final List<Expense> allExpenses = expenseRepository.findByAccountAndDateBetween(account, minDate, maxDate, Sort.by(Sort.Direction.DESC, "date", "id"));
        final List<Expense> expensesFromPDFMatched = new ArrayList<>();
        for (Expense expense : allExpenses) {
            List<Expense> foundedExpensesFromPDF = expensesFromPDF.stream()
                    .filter(expenseFromPDF -> {
                        // Distinto importe
                        if (!expenseFromPDF.getAmount().equals(expense.getAmount())) {
                            return false;
                        }

                        // Es un gasto normal, descarto si no coincide la fecha
                        if (expenseFromPDF.getDetails() != null) {
                            // Es un gasto en cuotas, si coincide el nro de cuota y el total, lo tomo como valido
                            return expense.getDetails().contains(expenseFromPDF.getDetails());
                        } else {
                            return expenseFromPDF.getDate().equals(expense.getDate());
                        }
                    })
                    .collect(Collectors.toList());
            expensesFounded.put(expense, foundedExpensesFromPDF);
            expensesFromPDFMatched.addAll(foundedExpensesFromPDF);
        }

        expensesNotFounded.addAll(expensesFromPDF.stream()
                .filter(expenseFromPDF -> !expensesFromPDFMatched.contains(expenseFromPDF))
                .collect(Collectors.toList())
        );

        return Pair.of(expensesFounded, expensesNotFounded);
    }
}