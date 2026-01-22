package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.api.galicia.model.BankAccountMovement;
import ar.com.personalfinances.api.galicia.model.Consumption;
import ar.com.personalfinances.api.galicia.model.CreditCardMovement;
import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.CategoryRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.service.*;
import ar.com.personalfinances.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
public class BankSyncController {

    private final Category automaticCategory;

    private final SpecificationsService specificationsService;
    private final AccountRepository accountRepository;
    private final ExpenseRepository expenseRepository;
    private final AlertEventService alertEventService;
    private final GaliciaApiService galiciaApiService;
    private final ExpenseMappingService expenseMappingService;
    private final ApplicationMessageService applicationMessageService;

    public BankSyncController(SpecificationsService specificationsService, AccountRepository accountRepository, ExpenseRepository expenseRepository, AlertEventService alertEventService, CategoryRepository categoryRepository, GaliciaApiService galiciaApiService, ExpenseMappingService expenseMappingService, ApplicationMessageService applicationMessageService) {
        this.specificationsService = specificationsService;
        this.accountRepository = accountRepository;
        this.expenseRepository = expenseRepository;
        this.alertEventService = alertEventService;
        this.automaticCategory = categoryRepository.findById(Category.AUTOMATIC_CATEGORY_ID).orElseThrow(() -> new ResourceNotFoundException("Category", "id", Category.AUTOMATIC_CATEGORY_ID));
        this.galiciaApiService = galiciaApiService;
        this.expenseMappingService = expenseMappingService;
        this.applicationMessageService = applicationMessageService;
    }

    @RequestMapping(value = "/bank-sync", method = RequestMethod.GET)
    public String getBankSyncPage(Model model) {
        BankSyncModelAttribute bankSyncModelAttribute = new BankSyncModelAttribute();

        final Date to = new Date();
        bankSyncModelAttribute.setDateTo(to);

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(to);
        calendar.add(Calendar.DATE, -7);
        bankSyncModelAttribute.setDateFrom(calendar.getTime());

        model.addAttribute("bankSyncModelAttribute", bankSyncModelAttribute);

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

    @RequestMapping(value = "/bank-sync", method = RequestMethod.POST)
    public String postBankSync(
            @Valid BankSyncModelAttribute bankSyncModelAttribute,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request) {
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

            if (!StringUtils.hasText(bankSyncModelAttribute.getCookie()) && account.getType().equals(AccountType.BANK_ACCOUNT)) {
                applicationMessageService.add(request, ApplicationMessage.error("Cookie null"));
                return "redirect:" + backUrl;
            }

            switch (account.getType()) {
                case CREDIT_CARD: {
                    CommonResult getMovimientosTarjetaResult = readCreditCardAccount(account);
                    if (getMovimientosTarjetaResult.isError()) {
                        applicationMessageService.add(request, ApplicationMessage.error(getMovimientosTarjetaResult.getMessage()));
                        return "redirect:" + backUrl;
                    } else {
                        applicationMessageService.add(request, ApplicationMessage.success(getMovimientosTarjetaResult.getMessage()));
                    }
                    break;
                }
                case BANK_ACCOUNT: {
                    if (bankSyncModelAttribute.getDateFrom() == null || bankSyncModelAttribute.getDateTo() == null) {
                        applicationMessageService.add(request, ApplicationMessage.error("Las fechas desde/hasta no pueden ser null"));
                        return "redirect:" + backUrl;
                    }

                    CommonResult syncBankAccountResult = syncBankAccount(bankSyncModelAttribute.getCookie(), account, bankSyncModelAttribute.getDateFrom(), bankSyncModelAttribute.getDateTo());
                    if (syncBankAccountResult.isError()) {
                        applicationMessageService.add(request, ApplicationMessage.error(syncBankAccountResult.getMessage()));
                        return "redirect:" + backUrl;
                    } else {
                        applicationMessageService.add(request, ApplicationMessage.success(syncBankAccountResult.getMessage()));
                    }

                    //learnFromBankMovements(bankSyncModelAttribute.getCookie(), account);

                    break;
                }
                default:
                    return "redirect:" + backUrl;
            }

            return "redirect:/expenses?accountType=" + account.getType().name() + "&accountName=" + account.getName();
        }
    }

    /*
     * Metodo para recorrer los movimientos desde hoy hacia atras con un delta de 3 meses hasta que no haya mas movimientos y luego te da un reporte de los que se repitieron mas de una vez
     */
    @SuppressWarnings("unused")
    public void learnFromBankMovements(String cookie, Account account) {
        final int monthsGap = -3;
        Date to = new Date();
        Date from = DateUtils.addMonths(to, monthsGap);
        CommonResult getMovimientosCuentaResult = CommonResult.ok();
        boolean hasMovements = true;

        // Mapa para contar las descripciones
        Map<String, Integer> descriptionCount = new HashMap<>();

        while (!getMovimientosCuentaResult.isError() && hasMovements) {
            final String strFrom = DateUtils.format(from);
            final String strTo = DateUtils.format(to);
            log.info("[learnFromMovements] Por buscar movimientos entre las fechas {} y {}", strFrom, strTo);
            getMovimientosCuentaResult = galiciaApiService.getMovimientosCuenta(ApplicationUtils.getGaliciaCredentials(), cookie, from, to);
            if (!getMovimientosCuentaResult.isError()) {
                List<BankAccountMovement> movements = (List<BankAccountMovement>) getMovimientosCuentaResult.getPayload();
                if (CollectionUtils.isEmpty(movements)) {
                    hasMovements = false;
                } else {
                    log.info("[syncBankAccount] Se recuperaron {} movimientos de la cuenta entre las fechas {} y {}.", movements.size(), strFrom, strTo);
                    for (BankAccountMovement movement : movements) {
                        descriptionCount.merge(getDescription(movement).toUpperCase(), 1, Integer::sum);
                    }

                    to = from;
                    from = DateUtils.addMonths(to, monthsGap);
                }
            }
        }

        if (getMovimientosCuentaResult.isError()) {
            log.error(getMovimientosCuentaResult.getMessage());
        } else {
            List<Map.Entry<String, Integer>> sortedDescriptions = descriptionCount.entrySet().stream()
                    .filter(entry -> entry.getValue() > 1) // Filtramos los que tienen más de 1 aparición
                    .filter(entry -> expenseMappingService.matchExpenseMapping(account.getOwner(), entry.getKey()).isEmpty())
                    .sorted(Comparator.comparing(Map.Entry<String, Integer>::getValue, Comparator.reverseOrder()).thenComparing(Map.Entry::getKey)) // Orden descendente por count y luego por description
                    .collect(Collectors.toList());

            sortedDescriptions.forEach(entry -> log.info("{}\t{}", entry.getValue(), entry.getKey()));
        }
    }

    private CommonResult syncBankAccount(String cookie, Account account, Date from, Date to) {
        final String strFrom = DateUtils.format(from);
        final String strTo = DateUtils.format(to);
        log.info("[syncBankAccount] Por sincronizar movimientos de la cuenta {} entre las fechas {} y {}", account.getName(), strFrom, strTo);
        CommonResult getMovimientosCuentaResult = galiciaApiService.getMovimientosCuenta(ApplicationUtils.getGaliciaCredentials(), cookie, from, to);
        if (getMovimientosCuentaResult.isError()) {
            return getMovimientosCuentaResult;
        }

        List<BankAccountMovement> movements = (List<BankAccountMovement>) getMovimientosCuentaResult.getPayload();
        if (CollectionUtils.isEmpty(movements)) {
            log.info("[syncBankAccount] No se recuperaron movimientos de la cuenta {} para sincronizar entre las fechas {} y {}", account.getName(), strFrom, strTo);
            return CommonResult.ok("No se recuperaron movimientos de la cuenta para sincronizar entre las fechas " + strFrom + " y " + strTo);
        }

        log.info("[syncBankAccount] Se recuperaron {} movimientos de la cuenta entre las fechas {} y {}. Se procede a filtrar los movimientos ya existentes", movements.size(), strFrom, strTo);
        final List<Long> expensesIdFounded = new ArrayList<>();
        movements = movements.stream().filter(movement -> {
            if (!movement.getMoneda().equals(GALICIA_CURRENCY_ARS_ID)) {
                log.info("[syncBankAccount] Se ignora el movimiento [{} {} {}] por moneda invalida: {}", DateUtils.format(movement.getFecha()), getDescription(movement), movement.getAmount(), movement.getMoneda());
                return false;
            }

            // Una minima validacion: el movimiento tiene que tener todos los datos minimos requeridos
            if (!isValid(movement)) {
                // Si el gasto no es valido, lo descarto
                return false;
            }

            // Me fijo en los gastos existentes si alguno coincide con el que movimiento del Galicia
            List<Expense> expensesByDateAndAmount = expenseRepository.findByAccountAndDateAndAmountEquals(account, movement.getFecha(), movement.getAmount());
            for (Expense expense : expensesByDateAndAmount) {
                if (expensesIdFounded.contains(expense.getId())) {
                    continue;
                }

                expensesIdFounded.add(expense.getId());
                return false;
            }

            // Si llegue a este punto, es que no encontre el gasto por cuenta, fecha e importe exacto, asi me fijo si tengo que buscar dias para atras hasta el proximo dia habil
            final Calendar cal = Calendar.getInstance();
            cal.setTime(movement.getFecha());
            boolean isWorkingDay = false;
            while (!isWorkingDay) {
                // Retrocedo un dia
                cal.add(Calendar.DATE, -1);
                if (DateUtils.isWeekend(cal) || DateUtils.esFeriado(cal.getTime())) {
                    expensesByDateAndAmount = expenseRepository.findByAccountAndDateAndAmountEquals(account, cal.getTime(), movement.getAmount());
                    for (Expense expense : expensesByDateAndAmount) {
                        if (expensesIdFounded.contains(expense.getId())) {
                            continue;
                        }

                        expensesIdFounded.add(expense.getId());
                        return false;
                    }
                } else {
                    isWorkingDay = true;
                }
            }

            // El gasto no existe en la DB y tiene los datos correctos. Lo guardo
            return true;
        }).collect(Collectors.toList());

        log.info("[syncBankAccount] Luego de filtrar los movimientos de la cuenta {} {}", account.getName(), movements.isEmpty()
                ? "no me quedaron movimientos por sincronizar"
                : "me quedaron " + movements.size() + " movimientos por sincronizar");

        if (movements.isEmpty()) {
            return CommonResult.ok(movements, "Los gastos de la cuenta estan sincronizados!");
        }

        final List<Expense> expensesCreated = new ArrayList<>();
        for (int i = movements.size() - 1; i >= 0; i--) {
            BankAccountMovement bankAccountMovement = movements.get(i);
            expensesCreated.add(createExpense(account.getOwner(), bankAccountMovement.getFecha(), account, getDescription(bankAccountMovement), bankAccountMovement.getAmount()));
        }

        return CommonResult.ok(expensesCreated, "Se " + (movements.size() > 1 ? "sincronizaron " + movements.size() + " gastos" : "sincronizo " + movements.size() + " gasto") +  " en la cuenta");
    }

    final long GALICIA_CURRENCY_ARS_ID = 1;
    private CommonResult syncCreditCardAccount(String cookie, Account account) {
        log.info("[syncCreditCardAccount] Por sincronizar movimientos de la tarjeta de credito {}", account.getName());
        CommonResult getMovimientosTarjetaResult = galiciaApiService.getMovimientosTarjeta(ApplicationUtils.getGaliciaCredentials(), cookie);
        if (getMovimientosTarjetaResult.isError()) {
            return getMovimientosTarjetaResult;
        }

        List<CreditCardMovement> movimientos = (List<CreditCardMovement>) getMovimientosTarjetaResult.getPayload();
        if (CollectionUtils.isEmpty(movimientos)) {
            log.info("[syncCreditCardAccount] No se recuperaron movimientos de la tarjeta de credito para sincronizar");
            return CommonResult.ok("No se recuperaron movimientos de la tarjeta de credito");
        }

        log.info("[syncCreditCardAccount] Se recuperaron {} movimientos de la tarjeta de credito. Se procede a filtrar los movimientos ya existentes", movimientos.size());
        final List<Long> expensesIdFounded = new ArrayList<>();
        movimientos = movimientos.stream().filter(movimiento -> {
            if (!movimiento.getCurrency().equals(GALICIA_CURRENCY_ARS_ID)) {
                log.info("[syncCreditCardAccount] Se ignora el movimiento [{} {} {}] por moneda invalida: {}", DateUtils.format(movimiento.getDate()), getDescription(movimiento), movimiento.getAmount(), movimiento.getCurrencySymbol());
                return false;
            } else if (movimiento.getTotalInstallment() != null && movimiento.getTotalInstallment() != 0) {
                log.info("[syncCreditCardAccount] Se ignora el movimiento [{} {} {}] por ser una cuota: {} de {}", DateUtils.format(movimiento.getDate()), getDescription(movimiento), movimiento.getAmount(), movimiento.getCurrentInstallment(), movimiento.getTotalInstallment());
                return false;
            }

            // Una minima validacion: el movimiento tiene que tener todos los datos minimos requeridos
            if (isValid(movimiento)) {
                // Me fijo en los gastos existentes si alguno coincide con el que movimiento del Galicia
                final List<Expense> expensesByDateAndAmount = expenseRepository.findByAccountAndDateAndAmountEquals(account, movimiento.getDate(), movimiento.getAmount());
                for (Expense expense : expensesByDateAndAmount) {
                    if (expensesIdFounded.contains(expense.getId())) {
                        continue;
                    }

                    expensesIdFounded.add(expense.getId());
                    return false;
                }
            } else {
                // Si el gasto no es valido, lo descarto
                return false;
            }

            // El gasto no existe en la DB y tiene los datos correctos. Lo guardo
            return true;
        }).collect(Collectors.toList());

        log.info("[syncCreditCardAccount] Luego de filtrar los movimientos de la tarjeta de credito {}", movimientos.isEmpty()
                ? "no me quedaron movimientos por sincronizar"
                : "me quedaron " + movimientos.size() + " movimientos por sincronizar");

        if (movimientos.isEmpty()) {
            return CommonResult.ok(movimientos, "Los gastos de la cuenta estan sincronizados!");
        }

        final List<Expense> expensesCreated = new ArrayList<>();
        for (int i = movimientos.size() - 1; i >= 0; i--) {
            CreditCardMovement creditCardMovement = movimientos.get(i);
            expensesCreated.add(createExpense(account.getOwner(), creditCardMovement.getDate(), account, getDescription(creditCardMovement), creditCardMovement.getAmount()));
        }

        return CommonResult.ok(expensesCreated, "Se sincronizaron " + movimientos.size() + " gastos en la cuenta");
    }

    private String getDescription(BankAccountMovement movimiento) {
        String description = movimiento.getDescripcionSide();
        if (!StringUtils.hasText(description)) {
            description = movimiento.getDescripcionAMostrar();
        } else if (!description.equals(movimiento.getDescripcionAMostrar())) {
            description += " | " + movimiento.getDescripcionAMostrar();
        }

        return description;
    }

    private String getDescription(CreditCardMovement creditCardMovement) {
        String description = creditCardMovement.getDescription();
        if (!StringUtils.hasText(description)) {
            description = creditCardMovement.getMovementDescription();
        } else if (!description.equals(creditCardMovement.getMovementDescription())) {
            description += " | " + creditCardMovement.getMovementDescription();
        }

        return description;
    }

    private String getDescription(Consumption consumption) {
//        String description = consumption.getDescription();
//        if (!StringUtils.hasText(description)) {
//            description = consumption.getMovementDescription();
//        } else if (!description.equals(consumption.getMovementDescription())) {
//            description += " | " + consumption.getMovementDescription();
//        }

        return consumption.getMerchantName();
    }

    private boolean isValid(BankAccountMovement bankAccountMovement) {
        if (bankAccountMovement.getFecha() == null) {
            log.info("[isValid] Invalid {}: fecha is null", bankAccountMovement);
            return false;
        } else if (bankAccountMovement.getDescripcionAMostrar() == null && bankAccountMovement.getDescripcionSide() == null) {
            log.info("[isValid] Invalid {}: descripcionAMostrar & descripcionSide is null", bankAccountMovement);
            return false;
        } else if (bankAccountMovement.getAmount() == null) {
            log.info("[isValid] Invalid {}: amount is null", bankAccountMovement);
            return false;
        }

        return true;
    }

    private boolean isValid(CreditCardMovement creditCardMovement) {
        if (creditCardMovement.getDate() == null) {
            log.info("[isValid] Invalid {}: fecha is null", creditCardMovement);
            return false;
        } else if (creditCardMovement.getDescription() == null && creditCardMovement.getMovementDescription() == null) {
            log.info("[isValid] Invalid {}: description & movementDescription is null", creditCardMovement);
            return false;
        } else if (creditCardMovement.getAmount() == null) {
            log.info("[isValid] Invalid {}: amount is null", creditCardMovement);
            return false;
        }

        return true;
    }

    private boolean isValid(Consumption consumption) {
        if (consumption.getTransactionDate() == null) {
            log.info("[isValid] Invalid {}: transaction date is null", consumption);
            return false;
        } else if (consumption.getMerchantName() == null) {
            log.info("[isValid] Invalid {}: merchant name is null", consumption);
            return false;
        } else if (consumption.getFinalAmount() == null) {
            log.info("[isValid] Invalid {}: final amount is null", consumption);
            return false;
        }

        return true;
    }

    private Expense createExpense(User user, Date date, Account account, String bankDescription, BigDecimal amount) {
        Expense expense = new Expense();
        expense.setUser(user);
        expense.setDate(date);
        expense.setAccount(account);
        expense.setAmount(amount);
        Optional<ExpenseMapping> matchOpt = expenseMappingService.matchExpenseMapping(user, bankDescription);
        if (matchOpt.isPresent()) {
            ExpenseMapping mapping = matchOpt.get();
            expense.setDescription(mapping.getNormalizedDescription());
            expense.setDetails(mapping.getDetails());
            expense.setCategory(mapping.getCategory() != null ? mapping.getCategory() : automaticCategory);
        } else {
            expense.setDescription(bankDescription);
            expense.setDetails(null);
            expense.setCategory(automaticCategory);
        }

        expense = expenseRepository.save(expense);
        log.info("[createExpense] Expense created: {} {} {}", DateUtils.format(expense.getDate()), expense.getDescription(), expense.getAmount());
        alertEventService.saveExpenseAlert(EntityEvent.CREATED, expense.getId(), "", user.getId());
        return expense;
    }

    private CommonResult readCreditCardAccount(Account creditCardAccount) {
        if (creditCardAccount.getType().equals(AccountType.CREDIT_CARD)) {
            String creditCardAccountName = creditCardAccount.getName();
            GaliciaApiService.CreditCardBrand creditCardBrand;
            String creditCardAccountNumber;
            if ("VISA".equals(creditCardAccountName)) {
                creditCardBrand = GaliciaApiService.CreditCardBrand.VISA;
                creditCardAccountNumber = "769200529";
            } else if ("Master Card".equals(creditCardAccountName)) {
                creditCardBrand = GaliciaApiService.CreditCardBrand.MASTER;
                creditCardAccountNumber = "1328457";
            } else {
                return CommonResult.error("La " + creditCardAccount + " no es una tarjeta de credito valida (VISA o Master Card)");
            }

            log.info("[readCreditCardAccount] Por sincronizar movimientos de la tarjeta de credito {}", creditCardAccount.getName());
            CommonResult getCardMovementsResult = galiciaApiService.getCardMovements(ApplicationUtils.getGaliciaCredentials(), creditCardBrand, creditCardAccountNumber);
            if (getCardMovementsResult.isError()) {
                return getCardMovementsResult;
            }

            List<Consumption> consumptions = (List<Consumption>) getCardMovementsResult.getPayload();
            if (CollectionUtils.isEmpty(consumptions)) {
                log.info("[readCreditCardAccount] No se recuperaron movimientos de la tarjeta de credito para sincronizar");
                return CommonResult.ok("No se recuperaron movimientos de la tarjeta de credito");
            }

            log.info("[readCreditCardAccount] Se recuperaron {} movimientos de la tarjeta de credito. Se procede a filtrar los movimientos ya existentes", consumptions.size());
            final List<Long> expensesIdFounded = new ArrayList<>();
            consumptions = consumptions.stream().filter(movimiento -> {
                if (!movimiento.getFinalCurrency().equals("ARS")) {
                    log.info("[readCreditCardAccount] Se ignora el movimiento [{} {} {}] por moneda invalida: {}", DateUtils.format(movimiento.getTransactionDate()), getDescription(movimiento), movimiento.getFinalAmount(), movimiento.getFinalCurrency());
                    return false;
                } else if (movimiento.getInstallmentPlan() != null && movimiento.getInstallmentPlan() != 0) {
                    log.info("[readCreditCardAccount] Se ignora el movimiento [{} {} {}] por ser una cuota: {} de {}", DateUtils.format(movimiento.getTransactionDate()), getDescription(movimiento), movimiento.getFinalAmount(), movimiento.getInstallmentNumber(), movimiento.getInstallmentPlan());
                    return false;
                }

                // Una minima validacion: el movimiento tiene que tener todos los datos minimos requeridos
                if (isValid(movimiento)) {
                    // Me fijo en los gastos existentes si alguno coincide con el que movimiento del Galicia
                    final List<Expense> expensesByDateAndAmount = expenseRepository.findByAccountAndDateAndAmountEquals(creditCardAccount, movimiento.getTransactionDate(), movimiento.getFinalAmount());
                    for (Expense expense : expensesByDateAndAmount) {
                        if (expensesIdFounded.contains(expense.getId())) {
                            continue;
                        }

                        expensesIdFounded.add(expense.getId());
                        return false;
                    }
                } else {
                    // Si el gasto no es valido, lo descarto
                    return false;
                }

                // El gasto no existe en la DB y tiene los datos correctos. Lo guardo
                return true;
            }).collect(Collectors.toList());

            log.info("[readCreditCardAccount] Luego de filtrar los movimientos de la tarjeta de credito {}", consumptions.isEmpty()
                    ? "no me quedaron movimientos por sincronizar"
                    : "me quedaron " + consumptions.size() + " movimientos por sincronizar");

            if (consumptions.isEmpty()) {
                return CommonResult.ok(consumptions, "Los gastos de la cuenta estan sincronizados!");
            }

            final List<Expense> expensesCreated = new ArrayList<>();
            for (int i = consumptions.size() - 1; i >= 0; i--) {
                Consumption creditCardMovement = consumptions.get(i);
                expensesCreated.add(createExpense(creditCardAccount.getOwner(), creditCardMovement.getTransactionDate(), creditCardAccount, getDescription(creditCardMovement), creditCardMovement.getFinalAmount()));
            }

            return CommonResult.ok(expensesCreated, "Se sincronizaron " + consumptions.size() + " gastos en la cuenta");
        } else {
            return CommonResult.error("La " + creditCardAccount + " no es una tarjeta de credito");
        }
    }
}