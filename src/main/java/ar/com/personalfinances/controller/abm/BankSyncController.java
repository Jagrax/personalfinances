package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.api.galicia.model.BankAccountMovement;
import ar.com.personalfinances.api.galicia.model.CreditCardMovement;
import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.CategoryRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.service.AlertEventService;
import ar.com.personalfinances.service.GaliciaApiService;
import ar.com.personalfinances.service.SpecificationsService;
import ar.com.personalfinances.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

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
    private final CategoryRepository categoryRepository;
    private final GaliciaApiService galiciaApiService;
    private final Map<String, String[]> movementMapByDescription;

    public BankSyncController(SpecificationsService specificationsService, AccountRepository accountRepository, ExpenseRepository expenseRepository, AlertEventService alertEventService, CategoryRepository categoryRepository, GaliciaApiService galiciaApiService) {
        this.specificationsService = specificationsService;
        this.accountRepository = accountRepository;
        this.expenseRepository = expenseRepository;
        this.alertEventService = alertEventService;
        this.automaticCategory = categoryRepository.findById(Category.AUTOMATIC_CATEGORY_ID).orElseThrow(() -> new ResourceNotFoundException("Category", "id", Category.AUTOMATIC_CATEGORY_ID));
        this.categoryRepository = categoryRepository;
        this.galiciaApiService = galiciaApiService;
        this.movementMapByDescription = new HashMap<>();
        movementMapByDescription.put("PERSONAL FLOW", new String[]{"Fibertel", "Servicio"});
        movementMapByDescription.put("AGUA Y SANEAMIEN", new String[]{"AySA", "Servicio"});
        movementMapByDescription.put("MERPAGO*CAFEVILLACRES", new String[]{"Cafetería - Café Villa Crespo", "Cefetería"});
        movementMapByDescription.put("MERPAGO*DONELADIO", new String[]{"Panadería - Don Eladio", "Gustito"});
        movementMapByDescription.put("MERPAGO*COTO", new String[]{"Supermercado - Coto", "Víveres para el hogar"});
        movementMapByDescription.put("LA FLOR DE ALMAGRO-SUC", new String[]{"Heladería - La Flor de Almagro", "Gustito"});
        movementMapByDescription.put("EMOVA SUBTE", new String[]{"Subte", "Movilidad"});
        movementMapByDescription.put("DEL PAN AND CIA", new String[]{"Panadería - La Nueva Villa Crespo", "Gustito"});
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
    public String postBankSync(@Valid BankSyncModelAttribute bankSyncModelAttribute, Model model) {

        if (bankSyncModelAttribute.getAccountId() == null) {
            return "redirect:/expenses";
        } else {
            Optional<Account> optionalAccount = accountRepository.findById(bankSyncModelAttribute.getAccountId());
            if (optionalAccount.isEmpty()) {
                return "redirect:/expenses";
            }

            final Account account = optionalAccount.get();

            String applicationMessage;
            ApplicationMessage.ApplicationMessageType applicationMessageType;
            switch (account.getType()) {
                case CREDIT_CARD: {
                    CommonResult getMovimientosTarjetaResult = syncCreditCardAccount(bankSyncModelAttribute.getCookie(), account);
                    if (getMovimientosTarjetaResult.isError()) {
                        model.addAttribute("applicationMessage", ApplicationMessage.error(getMovimientosTarjetaResult.getMessage()));
                        return "abm/bank-sync";
                    } else {
                        applicationMessage = getMovimientosTarjetaResult.getMessage();
                        applicationMessageType = ApplicationMessage.ApplicationMessageType.SUCCESS;
                    }
                    break;
                }
                case BANK_ACCOUNT: {
                    if (bankSyncModelAttribute.getDateFrom() == null || bankSyncModelAttribute.getDateTo() == null) {
                        model.addAttribute("applicationMessage", ApplicationMessage.error("Las fechas desde/hasta no pueden ser null"));
                        return "abm/bank-sync";
                    }

                    CommonResult syncBankAccountResult = syncBankAccount(bankSyncModelAttribute.getCookie(), account, bankSyncModelAttribute.getDateFrom(), bankSyncModelAttribute.getDateTo());
                    if (syncBankAccountResult.isError()) {
                        model.addAttribute("applicationMessage", ApplicationMessage.error(syncBankAccountResult.getMessage()));
                        return "abm/bank-sync";
                    } else {
                        applicationMessage = syncBankAccountResult.getMessage();
                        applicationMessageType = ApplicationMessage.ApplicationMessageType.SUCCESS;
                    }
                    break;
                }
                default:
                    return "redirect:/expenses";
            }

            String redirectUrl = "/expenses?accountType=" + account.getType().name() + "&accountName=" + account.getName();
            if (applicationMessage != null) redirectUrl += "&applicationMessage=" + applicationMessage;
            if (applicationMessageType != null) redirectUrl += "&applicationMessageType=" + applicationMessageType.name();
            return "redirect:" + redirectUrl;
        }
    }

    private CommonResult syncBankAccount(String cookie, Account account, Date from, Date to) {
        final String strFrom = DateUtils.format(from);
        final String strTo = DateUtils.format(to);
        log.info("[syncBankAccount] Por sincronizar movimientos de la cuenta {} entre las fechas {} y {}", account.getName(), strFrom, strTo);
        CommonResult getMovimientosCuentaResult = galiciaApiService.getMovimientosCuenta(cookie, from, to);
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
        CommonResult getMovimientosTarjetaResult = galiciaApiService.getMovimientosTarjeta(cookie);
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

        return mapDescription(description);
    }

    private String getDescription(CreditCardMovement creditCardMovement) {
        String description = creditCardMovement.getDescription();
        if (!StringUtils.hasText(description)) {
            description = creditCardMovement.getMovementDescription();
        } else if (!description.equals(creditCardMovement.getMovementDescription())) {
            description += " | " + creditCardMovement.getMovementDescription();
        }

        return mapDescription(description);
    }

    private Category getCategory(BankAccountMovement movimiento, User user) {
        String description = movimiento.getDescripcionAMostrar();
        if (!StringUtils.hasText(description)) {
            description = movimiento.getDescripcionAMostrar();
        } else if (!description.equals(movimiento.getDescripcionAMostrar())) {
            description += " | " + movimiento.getDescripcionAMostrar();
        }

        return mapCategory(description, user);
    }

    private Category getCategory(CreditCardMovement creditCardMovement, User user) {
        String description = creditCardMovement.getDescription();
        if (!StringUtils.hasText(description)) {
            description = creditCardMovement.getMovementDescription();
        } else if (!description.equals(creditCardMovement.getMovementDescription())) {
            description += " | " + creditCardMovement.getMovementDescription();
        }

        return mapCategory(description, user);
    }

    private String mapDescription(String description) {
        if (StringUtils.hasText(description) && movementMapByDescription.containsKey(description.toUpperCase())) {
            return movementMapByDescription.get(description.toUpperCase())[0];
        }
        return description;
    }

    private Category mapCategory(String description, User user) {
        if (StringUtils.hasText(description) && movementMapByDescription.containsKey(description.toUpperCase())) {
            String categoryName = movementMapByDescription.get(description.toUpperCase())[1];
            if (StringUtils.hasText(categoryName)) {
                Optional<Category> foundedCategory = categoryRepository.findByOwnerAndName(user, categoryName);
                if (foundedCategory.isPresent()) {
                    return foundedCategory.get();
                }
            }
        }
        return automaticCategory;
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

    private Expense createExpense(User user, Date date, Account account, String description, Category category, BigDecimal amount) {
        Expense expense = new Expense();
        expense.setUser(user);
        expense.setDate(date);
        expense.setAccount(account);
        expense.setAmount(amount);
        expense.setDescription(description);
        expense.setCategory(category);

        expense = expenseRepository.save(expense);
        log.info("[createExpense] Expense created: {} {} {}", DateUtils.format(expense.getDate()), expense.getDescription(), expense.getAmount());
        alertEventService.saveExpenseAlert(EntityEvent.CREATED, expense.getId(), "", user.getId());
        return expense;
    }
}