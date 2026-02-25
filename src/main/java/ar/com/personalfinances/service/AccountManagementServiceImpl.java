package ar.com.personalfinances.service;

import ar.com.personalfinances.api.galicia.model.BankAccountMovement;
import ar.com.personalfinances.api.galicia.model.Consumption;
import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.AccountApiCredentialsRepository;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.CategoryRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.util.CommonResult;
import ar.com.personalfinances.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccountManagementServiceImpl implements AccountManagementService {

    private final AccountApiCredentialsRepository accountApiCredentialsRepository;
    private final GaliciaApiService galiciaApiService;
    private final ExpenseRepository expenseRepository;
    private final AccountRepository accountRepository;
    private final AlertEventService alertEventService;
    private final ExpenseMappingService expenseMappingService;
    private final Category automaticCategory;

    public AccountManagementServiceImpl(AccountApiCredentialsRepository accountApiCredentialsRepository, GaliciaApiService galiciaApiService, ExpenseRepository expenseRepository, AccountRepository accountRepository, AlertEventService alertEventService, ExpenseMappingService expenseMappingService, CategoryRepository categoryRepository) {
        this.accountApiCredentialsRepository = accountApiCredentialsRepository;
        this.galiciaApiService = galiciaApiService;
        this.expenseRepository = expenseRepository;
        this.accountRepository = accountRepository;
        this.alertEventService = alertEventService;
        this.expenseMappingService = expenseMappingService;
        this.automaticCategory = categoryRepository.findById(Category.AUTOMATIC_CATEGORY_ID).orElseThrow(() -> new ResourceNotFoundException("Category", "id", Category.AUTOMATIC_CATEGORY_ID));
    }

    @Override
    public CommonResult syncAccountMovements(Account account, String aspNetSessionId) {
        if (!AccountType.BANK_ACCOUNT.equals(account.getType())) {
            return CommonResult.warn("The requested account to sync is not a bank account: " + account);
        }

        if (!account.isSyncEnabled()) {
            return CommonResult.warn("The requested account to sync is not allowed for sync: " + account);
        }

        final Date to = new Date();

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(to);
        calendar.add(Calendar.DATE, -30);
        final Date from = calendar.getTime();

        final String strFrom = DateUtils.format(from);
        final String strTo = DateUtils.format(to);
        log.info("[syncBankAccount] Por sincronizar movimientos de la cuenta {} entre las fechas {} y {}", account.getName(), strFrom, strTo);
        CommonResult getMovimientosCuentaResult = galiciaApiService.getMovimientosCuenta(aspNetSessionId, from, to);
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
            if (movement.getFecha() == null) {
                log.info("[isValid] Invalid {}: fecha is null", movement);
                return false;
            } else if (movement.getDescripcionAMostrar() == null && movement.getDescripcionSide() == null) {
                log.info("[isValid] Invalid {}: descripcionAMostrar & descripcionSide is null", movement);
                return false;
            } else if (movement.getAmount() == null) {
                log.info("[isValid] Invalid {}: amount is null", movement);
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

    /*
     * Metodo para recorrer los movimientos desde hoy hacia atras con un delta de 3 meses hasta que no haya mas movimientos y luego te da un reporte de los que se repitieron mas de una vez
     */
    @Override
    public CommonResult learnFromBankMovements(Account account, String appNetSessionId) {
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
            getMovimientosCuentaResult = galiciaApiService.getMovimientosCuenta(appNetSessionId, from, to);

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
            return getMovimientosCuentaResult;
        }

        return CommonResult.ok(descriptionCount.entrySet().stream()
                    .filter(entry -> entry.getValue() > 1) // Filtramos los que tienen más de 1 aparición
                    .filter(entry -> expenseMappingService.matchExpenseMapping(account.getOwner(), entry.getKey()).isEmpty())
                    .sorted(Comparator.comparing(Map.Entry<String, Integer>::getValue, Comparator.reverseOrder()).thenComparing(Map.Entry::getKey)) // Orden descendente por count y luego por description
                    .collect(Collectors.toList())
        );
    }

    @Override
    public CommonResult syncCreditCardAccountMovements(Account creditCardAccount) {
        if (!AccountType.CREDIT_CARD.equals(creditCardAccount.getType())) {
            return CommonResult.warn("The requested account to sync is not a credit card: " + creditCardAccount);
        }

        if (!creditCardAccount.isSyncEnabled()) {
            return CommonResult.warn("The requested account to sync is not allowed for sync: " + creditCardAccount);
        }

        final Optional<AccountApiCredentials> optAccountApiCredentials = accountApiCredentialsRepository.findByAccount(creditCardAccount);
        if (optAccountApiCredentials.isEmpty()) {
            return CommonResult.warn("The requested account to sync does not have API credentials: " + creditCardAccount);
        }

        final AccountApiCredentials accountApiCredentials = optAccountApiCredentials.get();
        if (SyncProvider.GALICIA.equals(accountApiCredentials.getProvider())) {
            final String extraDataEncrypted = accountApiCredentials.getExtraDataEncrypted();
            if (!StringUtils.hasText(extraDataEncrypted)) {
                return CommonResult.warn("The requested account to sync does not have API credentials: " + creditCardAccount);
            }

            String usernameEncrypted = accountApiCredentials.getUsernameEncrypted();
            if (!StringUtils.hasText(usernameEncrypted)) {
                throw new IllegalArgumentException("The API credentials has not username: " + accountApiCredentials);
            }
            final String[] dniAndUsername = usernameEncrypted.split("\\|", 2);
            final String galiciaUserDNI = dniAndUsername[0];
            final String galiciaUserName = dniAndUsername[1];
            final String galiciaUserPassword = accountApiCredentials.getPasswordEncrypted();

            final String[] accountBrandAndNumber = extraDataEncrypted.split("\\|", 2);
            final GaliciaApiService.CreditCardBrand creditCardBrand = GaliciaApiService.CreditCardBrand.valueOf(accountBrandAndNumber[0]);
            final String galiciaAccountNumber = accountBrandAndNumber[1];

            log.info("[syncCreditCardAccountMovements] Por sincronizar movimientos de la tarjeta de credito {}", creditCardAccount.getName());
            CommonResult getCardMovementsResult = galiciaApiService.getCardMovements(galiciaUserDNI, galiciaUserName, galiciaUserPassword, creditCardBrand, galiciaAccountNumber);
            if (getCardMovementsResult.isError()) {
                return getCardMovementsResult;
            }

            List<Consumption> consumptions = (List<Consumption>) getCardMovementsResult.getPayload();
            if (CollectionUtils.isEmpty(consumptions)) {
                log.info("[syncCreditCardAccountMovements] No se recuperaron movimientos de la tarjeta de credito para sincronizar");
                return CommonResult.ok(consumptions, "No se recuperaron movimientos de la tarjeta de credito");
            }

            log.info("[syncCreditCardAccountMovements] Se recuperaron {} movimientos de la tarjeta de credito. Se procede a filtrar los movimientos ya existentes", consumptions.size());

            // Filtro los consumos validos
            consumptions = consumptions.stream()
                    .filter(consumption -> {
                        // Una minima validacion: el movimiento tiene que tener todos los datos minimos requeridos
                        if (consumption.getTransactionDate() == null) {
                            log.info("[isValid] Invalid {}: transaction date is null", consumption);
                            return false;
                        } else if (consumption.getMerchantName() == null) {
                            log.info("[isValid] Invalid {}: merchant name is null", consumption);
                            return false;
                        } else if (consumption.getFinalAmount() == null) {
                            log.info("[isValid] Invalid {}: final amount is null", consumption);
                            return false;
                        } else if (!consumption.getFinalCurrency().equals("ARS")) {
                            log.info("[readCreditCardAccount] Se ignora el movimiento [{} {} {}] por moneda invalida: {}", DateUtils.format(consumption.getTransactionDate()), consumption.getMerchantName(), consumption.getFinalAmount(), consumption.getFinalCurrency());
                            return false;
                        }

                        return true;
                    })
                    .sorted(Comparator.comparing(Consumption::getTransactionDate))
                    .collect(Collectors.toList());

            final List<Long> expensesIdFounded = new ArrayList<>();
            Date minTransactionDate = null;
            Date maxTransactionDate = null;

            List<Consumption> consumptionsToCreate = new ArrayList<>();
            for (Consumption consumption : consumptions) {
                final boolean isQuota = consumption.getInstallmentPlan() != null && consumption.getInstallmentPlan() > 0;
                final List<Expense> foundedExpenses;
                // Me fijo en los gastos existentes si alguno coincide con el que movimiento del Galicia
                if (isQuota) {
                    log.debug("Por buscar gasto con cuota {} de {} por {}", consumption.getInstallmentNumber(), consumption.getInstallmentPlan(), consumption.getFinalAmount());
                    foundedExpenses = expenseRepository.findByAccountAndAmountEqualsAndDetailsLike(creditCardAccount, consumption.getFinalAmount(), "%Cuota " + consumption.getInstallmentNumber() + " de " + consumption.getInstallmentPlan() + "%");
                } else {
                    if (minTransactionDate == null || consumption.getTransactionDate().before(minTransactionDate)) {
                        minTransactionDate = consumption.getTransactionDate();
                    }
                    if (maxTransactionDate == null || consumption.getTransactionDate().after(maxTransactionDate)) {
                        maxTransactionDate = consumption.getTransactionDate();
                    }
                    log.debug("Por buscar gasto del {} por {}. Consumption.desc: {}", DateUtils.format(consumption.getTransactionDate()), consumption.getFinalAmount(), consumption.getMerchantName());
                    foundedExpenses = expenseRepository.findByAccountAndDateAndAmountEquals(creditCardAccount, consumption.getTransactionDate(), consumption.getFinalAmount());
                }

                boolean found = false;
                for (Expense expense : foundedExpenses) {
                    if (expensesIdFounded.contains(expense.getId())) {
                        continue;
                    }

                    expensesIdFounded.add(expense.getId());
                    found = true;
                }

                // El gasto no existe en la DB y tiene los datos correctos. Lo guardo
                if (!found) {
                    if (isQuota) {
                        log.info("No se encontra la expense para el {}", consumption);
                    } else {
                        consumptionsToCreate.add(consumption);
                    }
                }
            }

            log.info("[syncCreditCardAccountMovements] Luego de filtrar los movimientos de la tarjeta de credito {}", consumptionsToCreate.isEmpty()
                    ? "no me quedaron movimientos por sincronizar"
                    : "me quedaron " + consumptionsToCreate.size() + " movimientos por sincronizar");

            final List<Expense> expensesCreated;
            final String resultMessage;
            if (consumptionsToCreate.isEmpty()) {
                expensesCreated = new ArrayList<>();
                resultMessage = "Los gastos de la cuenta estan sincronizados!";
            } else {
                expensesCreated = consumptionsToCreate.stream().map(consumption -> createExpense(creditCardAccount.getOwner(), consumption.getTransactionDate(), creditCardAccount, consumption.getMerchantName(), consumption.getFinalAmount())).collect(Collectors.toList());
                resultMessage = "Se sincronizaron " + consumptionsToCreate.size() + " gastos en la cuenta";
            }

            List<Expense> creditCardAccountExpensesByDates = expenseRepository.findByAccountAndDateBetween(creditCardAccount, minTransactionDate, maxTransactionDate, Sort.by(Sort.Direction.DESC, "date", "id"));
            List<Expense> expensesNotFoundInConsuptions = creditCardAccountExpensesByDates.stream()
                    // Filtro a los no encontrados y además, los que sean pagos de tarjetas (no vienen en la API)
                    .filter(expense -> !expensesIdFounded.contains(expense.getId()) && !"Pago de tarjeta".equals(expense.getCategory().getName()))
                    .collect(Collectors.toList());
            if (!expensesNotFoundInConsuptions.isEmpty()) {
                BigDecimal amount = BigDecimal.ZERO;
                log.info("Los siguientes gastos no fueron encontrados al sincronizar con el banco:");
                for (Expense expense : expensesNotFoundInConsuptions) {
                    log.info("{} - {} {}", DateUtils.format(expense.getDate()), expense.getDescription(), expense.getAmount());
                    amount = amount.add(expense.getAmount());
                }
                log.info("En total, estos gastos suman {}", amount);
            }

            return CommonResult.ok(expensesCreated, resultMessage);
        } else {
            throw new IllegalArgumentException("AccountAPICredentials.provider invalid [" + accountApiCredentials.getProvider() + "]");
        }
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

    @Override
    @Transactional
    public CommonResult syncUserAccounts(User user) {
        final List<Account> syncedAccounts = new ArrayList<>();
        final List<Account> userAccounts = accountRepository.findByOwner(user);
        for (Account userAccount : userAccounts) {
            if (userAccount.isSyncEnabled() && userAccount.getType() == AccountType.CREDIT_CARD
                    && (userAccount.getLastSyncAt() == null || userAccount.getLastSyncAt().isBefore(LocalDateTime.now().minusMinutes(30)))) {

                CommonResult syncResult = syncCreditCardAccountMovements(userAccount);
                if (syncResult.isError()) {
                    return CommonResult.error("Error del Galicia al sincronizar la cuenta [" + userAccount.getId() + "|" + userAccount.getName() + "]: " + syncResult.getMessage());
                } else if (syncResult.isWarning()) {
                    return CommonResult.warn("Error del configuracion/validacion al sincronizar la cuenta [" + userAccount.getId() + "|" + userAccount.getName() + "]: " + syncResult.getMessage());
                } else {
                    userAccount.setLastSyncAt(LocalDateTime.now());
                    syncedAccounts.add(userAccount);
                }
            }
        }

        return CommonResult.ok(syncedAccounts);
    }
}