package ar.com.personalfinances.service;

import ar.com.personalfinances.api.galicia.model.BankAccountMovement;
import ar.com.personalfinances.api.galicia.model.Consumption;
import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.CategoryRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.util.CommonResult;
import ar.com.personalfinances.util.DateUtils;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccountManagementServiceImpl implements AccountManagementService {

    private final GaliciaApiService galiciaApiService;
    private final ExpenseRepository expenseRepository;
    private final AccountRepository accountRepository;
    private final AlertEventService alertEventService;
    private final ExpenseMappingService expenseMappingService;
    private final Category automaticCategory;
    private final ChartJsServiceImpl chartJsServiceImpl;

    public AccountManagementServiceImpl(GaliciaApiService galiciaApiService, ExpenseRepository expenseRepository, AccountRepository accountRepository, AlertEventService alertEventService, ExpenseMappingService expenseMappingService, CategoryRepository categoryRepository, ChartJsServiceImpl chartJsServiceImpl) {
        this.galiciaApiService = galiciaApiService;
        this.expenseRepository = expenseRepository;
        this.accountRepository = accountRepository;
        this.alertEventService = alertEventService;
        this.expenseMappingService = expenseMappingService;
        this.automaticCategory = categoryRepository.findById(Category.AUTOMATIC_CATEGORY_ID).orElseThrow(() -> new ResourceNotFoundException("Category", "id", Category.AUTOMATIC_CATEGORY_ID));
        this.chartJsServiceImpl = chartJsServiceImpl;
    }

    private CommonResult validateBankAccountToSync(Account account) {
        if (!AccountType.BANK_ACCOUNT.equals(account.getType())) {
            return CommonResult.warn("The requested account to sync is not a bank account: " + account);
        }

        if (!account.isSyncEnabled()) {
            return CommonResult.warn("The requested account to sync is not allowed for sync: " + account);
        }

        if (!StringUtils.hasText(account.getExternalAccountId())) {
            return CommonResult.warn("Account externalAccountId is required but is null or empty: " + account);
        } else if (!account.getExternalAccountId().contains("|")) {
            return CommonResult.warn("Invalid externalAccountId format (expected 'tipo|index'): " + account.getExternalAccountId());
        }

        return CommonResult.ok();
    }

    @Override
    public CommonResult syncAccountMovements(Account account, String cookies) {
        CommonResult validationResult = validateBankAccountToSync(account);
        if (validationResult.isError() || validationResult.isWarning()) {
            return validationResult;
        }

        if (!StringUtils.hasText(cookies)) {
            return CommonResult.warn("Galicia cookies are required for bank account sync");
        }

        final String[] parts = account.getExternalAccountId().split("\\|", 2);
        final String accountTipo = parts[0];
        final String accountIndex = parts[1];

        CommonResult cuentasSessionResult = galiciaApiService.establishCuentasSession(cookies);
        if (cuentasSessionResult.isError()) {
            return cuentasSessionResult;
        }
        String cuentasCookies = (String) cuentasSessionResult.getPayload();

        log.trace("[syncBankAccount] Selecting account tipo={}, index={}", accountTipo, accountIndex);
        CommonResult selectResult = galiciaApiService.selectAccount(cuentasCookies, accountTipo, accountIndex);
        if (selectResult.isError()) {
            log.warn("[syncBankAccount] selectAccount failed: {}", selectResult.getMessage());
            return CommonResult.warn("No se pudo seleccionar la cuenta en Galicia Cuentas: " + selectResult.getMessage());
        }
        // Use updated cookies from selectAccount (may include new cookies from redirect)
        cuentasCookies = (String) selectResult.getPayload();

        final LocalDate to = LocalDate.now();
        final LocalDate from = to.minusDays(30);

        final String strFrom = DateUtils.format(from);
        final String strTo = DateUtils.format(to);
        log.info("[syncBankAccount] Por sincronizar movimientos de la cuenta {} entre las fechas {} y {}", account.getName(), strFrom, strTo);
        CommonResult getMovimientosCuentaResult = galiciaApiService.getMovimientosCuenta(cuentasCookies, from, to);
        if (getMovimientosCuentaResult.isError()) {
            return getMovimientosCuentaResult;
        }

        List<BankAccountMovement> movements = (List<BankAccountMovement>) getMovimientosCuentaResult.getPayload();
        if (CollectionUtils.isEmpty(movements)) {
            log.info("[syncBankAccount] No se recuperaron movimientos de la cuenta {} para sincronizar entre las fechas {} y {}", account.getName(), strFrom, strTo);
            markAccountAsSynced(account);
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

                if (!StringUtils.hasText(expense.getOriginalDescription())) {
                    String bankDesc = getDescription(movement);
                    expense.setOriginalDescription(bankDesc);
                    expenseRepository.save(expense);
                    log.info("[syncBankAccount] Actualizado originalDescription del gasto {}: {}", expense.getId(), bankDesc);
                }

                expensesIdFounded.add(expense.getId());
                return false;
            }

            // Si llegue a este punto, es que no encontre el gasto por cuenta, fecha e importe exacto, asi me fijo si tengo que buscar dias para atras hasta el proximo dia habil
            LocalDate date = movement.getFecha();
            boolean isWorkingDay = false;
            while (!isWorkingDay) {
                // Retrocedo un dia
                date = date.minusDays(1);
                if (DateUtils.isWeekend(date) || DateUtils.esFeriado(date)) {
                    expensesByDateAndAmount = expenseRepository.findByAccountAndDateAndAmountEquals(account, date, movement.getAmount());
                    for (Expense expense : expensesByDateAndAmount) {
                        if (expensesIdFounded.contains(expense.getId())) {
                            continue;
                        }

                        if (!StringUtils.hasText(expense.getOriginalDescription())) {
                            String bankDesc = getDescription(movement);
                            expense.setOriginalDescription(bankDesc);
                            expenseRepository.save(expense);
                            log.info("[syncBankAccount] Actualizado originalDescription del gasto {}: {}", expense.getId(), bankDesc);
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
            markAccountAsSynced(account);
            return CommonResult.ok(movements, "Los gastos de la cuenta estan sincronizados!");
        }

        final List<Expense> expensesCreated = new ArrayList<>();
        for (int i = movements.size() - 1; i >= 0; i--) {
            BankAccountMovement bankAccountMovement = movements.get(i);
            expensesCreated.add(createExpense(account.getOwner(), bankAccountMovement.getFecha(), account, getDescription(bankAccountMovement), bankAccountMovement.getAmount()));
        }

        markAccountAsSynced(account);
        return CommonResult.ok(expensesCreated, "Se " + (movements.size() > 1 ? "sincronizaron " + movements.size() + " gastos" : "sincronizo " + movements.size() + " gasto") +  " en la cuenta");
    }

    /*
     * Metodo para recorrer los movimientos desde hoy hacia atras con un delta de 3 meses hasta que no haya mas movimientos y luego te da un reporte de los que se repitieron mas de una vez
     */
    @Override
    public CommonResult learnFromBankMovements(Account account, String cookies) {
        CommonResult validationResult = validateBankAccountToSync(account);
        if (validationResult.isError() || validationResult.isWarning()) {
            return validationResult;
        }

        if (!StringUtils.hasText(cookies)) {
            return CommonResult.warn("Galicia cookies are required for bank account learning");
        }

        final String[] parts = account.getExternalAccountId().split("\\|", 2);
        final String accountTipo = parts[0];
        final String accountIndex = parts[1];

        CommonResult cuentasSessionResult = galiciaApiService.establishCuentasSession(cookies);
        if (cuentasSessionResult.isError()) {
            return cuentasSessionResult;
        }
        String cuentasCookies = (String) cuentasSessionResult.getPayload();

        log.trace("[learnFromMovements] Selecting account tipo={}, index={}", accountTipo, accountIndex);
        CommonResult selectResult = galiciaApiService.selectAccount(cuentasCookies, accountTipo, accountIndex);
        if (selectResult.isError()) {
            log.warn("[learnFromMovements] selectAccount failed: {}", selectResult.getMessage());
            return CommonResult.warn("No se pudo seleccionar la cuenta en Galicia Cuentas: " + selectResult.getMessage());
        }
        // Use updated cookies from selectAccount (may include new cookies from redirect)
        cuentasCookies = (String) selectResult.getPayload();

        final int monthsGap = 3;
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(monthsGap);
        CommonResult getMovimientosCuentaResult = CommonResult.ok();
        boolean hasMovements = true;

        // Mapa para contar las descripciones
        Map<String, Integer> descriptionCount = new HashMap<>();

        while (!getMovimientosCuentaResult.isError() && hasMovements) {
            final String strFrom = DateUtils.format(from);
            final String strTo = DateUtils.format(to);
            log.info("[learnFromMovements] Por buscar movimientos entre las fechas {} y {}", strFrom, strTo);
            getMovimientosCuentaResult = galiciaApiService.getMovimientosCuenta(cuentasCookies, from, to);

            if (!getMovimientosCuentaResult.isError()) {
                List<BankAccountMovement> movements = (List<BankAccountMovement>) getMovimientosCuentaResult.getPayload();
                if (CollectionUtils.isEmpty(movements)) {
                    hasMovements = false;
                } else {
                    log.info("[learnFromBankMovements] Se recuperaron {} movimientos de la cuenta entre las fechas {} y {}.", movements.size(), strFrom, strTo);
                    for (BankAccountMovement movement : movements) {
                        descriptionCount.merge(getDescription(movement).toUpperCase(), 1, Integer::sum);
                    }

                    to = from;
                    from = to.minusMonths(monthsGap);
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
    public CommonResult syncCreditCardAccountMovements(Account creditCardAccount, String cookies) {
        if (!AccountType.CREDIT_CARD.equals(creditCardAccount.getType())) {
            return CommonResult.warn("The requested account to sync is not a credit card: " + creditCardAccount);
        }

        if (!creditCardAccount.isSyncEnabled()) {
            return CommonResult.warn("The requested account to sync is not allowed for sync: " + creditCardAccount);
        }

        if (!StringUtils.hasText(creditCardAccount.getExternalAccountId())) {
            return CommonResult.warn("The requested account to sync does not have an external account ID: " + creditCardAccount);
        }

        if (!StringUtils.hasText(cookies)) {
            return CommonResult.warn("Galicia cookies are required for credit card sync");
        }


        if (SyncProvider.GALICIA.equals(creditCardAccount.getSyncProvider())) {

            final GaliciaApiService.CreditCardBrand creditCardBrand;
            if ("VISA".equals(creditCardAccount.getName()) || "Visa".equalsIgnoreCase(creditCardAccount.getName())) {
                creditCardBrand = GaliciaApiService.CreditCardBrand.VISA;
            } else if ("Master Card".equals(creditCardAccount.getName()) || "MASTER".equalsIgnoreCase(creditCardAccount.getName())) {
                creditCardBrand = GaliciaApiService.CreditCardBrand.MASTER;
            } else {
                return CommonResult.warn("Unknown credit card brand for account: " + creditCardAccount);
            }

            final String galiciaAccountNumber = creditCardAccount.getExternalAccountId();
            log.info("[syncCreditCardAccountMovements] Por sincronizar movimientos de la tarjeta de credito {} con externalAccountId {}", creditCardAccount.getName(), galiciaAccountNumber);
            CommonResult getCardMovementsResult = galiciaApiService.getCardMovements(cookies, creditCardBrand, galiciaAccountNumber);
            if (getCardMovementsResult.isError()) {
                return getCardMovementsResult;
            }

            List<Consumption> consumptions = (List<Consumption>) getCardMovementsResult.getPayload();
            if (CollectionUtils.isEmpty(consumptions)) {
                log.info("[syncCreditCardAccountMovements] No se recuperaron movimientos de la tarjeta de credito para sincronizar");
                markAccountAsSynced(creditCardAccount);
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

            final Set<Long> expensesIdFounded = new HashSet<>();
            final LocalDate periodStart = chartJsServiceImpl.resolvePeriodStart(creditCardAccount);
            LocalDate minTransactionDate = null;
            LocalDate maxTransactionDate = null;

            List<Consumption> consumptionsToCreate = new ArrayList<>();
            for (Consumption consumption : consumptions) {
                final boolean isQuota = consumption.getInstallmentPlan() != null && consumption.getInstallmentPlan() > 0;
                final List<Expense> foundedExpenses;
                // Me fijo en los gastos existentes si alguno coincide con el que movimiento del Galicia
                if (isQuota) {
                    log.debug("Por buscar gasto con cuota {} de {} por {}", consumption.getInstallmentNumber(), consumption.getInstallmentPlan(), consumption.getFinalAmount());
                    foundedExpenses = expenseRepository.findByAccountAndAmountEqualsAndDetailsLike(creditCardAccount, consumption.getFinalAmount(), "%Cuota " + consumption.getInstallmentNumber() + " de " + consumption.getInstallmentPlan() + "%");
                } else {
                    // Solo contemplo las fechas si el consumo es de este periodo (cuotas/devoluciones pueden tener como fecha de transaccion la fecha de compra)
                    if (consumption.getTransactionDate().isAfter(periodStart)) {
                        if (minTransactionDate == null || consumption.getTransactionDate().isBefore(minTransactionDate)) minTransactionDate = consumption.getTransactionDate();
                        if (maxTransactionDate == null || consumption.getTransactionDate().isAfter(maxTransactionDate))  maxTransactionDate = consumption.getTransactionDate();
                    }
                    log.debug("Por buscar gasto del {} por {}. Consumption.desc: {}", DateUtils.format(consumption.getTransactionDate()), consumption.getFinalAmount(), consumption.getMerchantName());
                    foundedExpenses = expenseRepository.findByAccountAndDateAndAmountEquals(creditCardAccount, consumption.getTransactionDate(), consumption.getFinalAmount());
                }

                boolean found = false;
                for (Expense expense : foundedExpenses) {
                    if (expensesIdFounded.contains(expense.getId())) {
                        continue;
                    }

                    if (!StringUtils.hasText(expense.getOriginalDescription())) {
                        expense.setOriginalDescription(consumption.getMerchantName());
                        expenseRepository.save(expense);
                        log.info("[syncCreditCardAccountMovements] Actualizado originalDescription del gasto {}: {}", expense.getId(), consumption.getMerchantName());
                    }

                    expensesIdFounded.add(expense.getId());
                    found = true;
                    break;
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
                // Agrego los gastos recien creados al listado de IDs de gastos encontrados
                expensesCreated.forEach(expenseCreated -> expensesIdFounded.add(expenseCreated.getId()));
                resultMessage = "Se sincronizaron " + consumptionsToCreate.size() + " gastos en la cuenta";
            }

            List<Expense> creditCardAccountExpensesByDates = expenseRepository.findByAccountAndDateBetween(creditCardAccount, minTransactionDate, maxTransactionDate, Sort.by(Sort.Direction.DESC, "date", "id"));
            List<Expense> expensesNotFoundInConsuptions = creditCardAccountExpensesByDates.stream()
                    // Filtro a los no encontrados y además, los que sean pagos de tarjetas (no vienen en la API)
                    .filter(expense -> !expensesIdFounded.contains(expense.getId()) && !"Pago de tarjeta".equals(expense.getCategory().getName()))
                    .toList();
            if (!expensesNotFoundInConsuptions.isEmpty()) {
                BigDecimal amount = BigDecimal.ZERO;
                log.info("Los siguientes gastos no fueron encontrados al sincronizar con el banco:");
                for (Expense expense : expensesNotFoundInConsuptions) {
                    log.info("{} - {} {}", DateUtils.format(expense.getDate()), expense.getDescription(), expense.getAmount());
                    amount = amount.add(expense.getAmount());
                }
                log.info("En total, estos gastos suman {}", amount);
            }

            markAccountAsSynced(creditCardAccount);
            return CommonResult.ok(expensesCreated, resultMessage);
        } else {
            throw new IllegalArgumentException("AccountAPICredentials.provider invalid [" + /*accountApiCredentials.getProvider() + */"]");
        }
    }

    private void markAccountAsSynced(Account account) {
        account.setLastSyncAt(LocalDateTime.now());
        accountRepository.save(account);
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

    private Expense createExpense(User user, LocalDate date, Account account, String bankDescription, BigDecimal amount) {
        Expense expense = new Expense();
        expense.setUser(user);
        expense.setDate(date);
        expense.setAccount(account);
        expense.setAmount(amount);
        expense.setOriginalDescription(bankDescription);
        Optional<ExpenseMapping> matchOpt = expenseMappingService.matchExpenseMapping(user, bankDescription);
        if (matchOpt.isPresent()) {
            ExpenseMapping mapping = matchOpt.get();
            expense.setDescription(StringUtils.hasText(mapping.getNormalizedDescription()) ? mapping.getNormalizedDescription() : bankDescription);
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
    public CommonResult syncUserAccounts(User user, String cookies) {
        final List<Account> syncedAccounts = new ArrayList<>();
        final List<Account> userAccounts = accountRepository.findByOwner(user);
        for (Account userAccount : userAccounts) {
            if (userAccount.isSyncEnabled() && userAccount.getType() == AccountType.CREDIT_CARD
                    && (userAccount.getLastSyncAt() == null || userAccount.getLastSyncAt().isBefore(LocalDateTime.now().minusMinutes(30)))) {

                CommonResult syncResult = syncCreditCardAccountMovements(userAccount, cookies);
                if (syncResult.isError()) {
                    return CommonResult.error("Error del Galicia al sincronizar la cuenta [" + userAccount.getId() + "|" + userAccount.getName() + "]: " + syncResult.getMessage());
                } else if (syncResult.isWarning()) {
                    return CommonResult.warn("Error del configuracion/validacion al sincronizar la cuenta [" + userAccount.getId() + "|" + userAccount.getName() + "]: " + syncResult.getMessage());
                } else {
                    syncedAccounts.add(userAccount);
                }
            }
        }

        return CommonResult.ok(syncedAccounts);
    }
}
