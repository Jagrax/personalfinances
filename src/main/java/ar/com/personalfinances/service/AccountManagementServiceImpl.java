package ar.com.personalfinances.service;

import ar.com.personalfinances.api.galicia.model.BankAccountMovement;
import ar.com.personalfinances.api.galicia.model.Consumption;
import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.util.CommonResult;
import ar.com.personalfinances.util.DateUtils;
import ar.com.personalfinances.util.SyncResult;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccountManagementServiceImpl implements AccountManagementService {

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.50");
    private static final Pattern INSTALLMENT_DETAILS_PATTERN = Pattern.compile("Cuota \\d+ de \\d+\\s*$");

    private final GaliciaApiService galiciaApiService;
    private final ExpenseRepository expenseRepository;
    private final AccountRepository accountRepository;
    private final AlertEventService alertEventService;
    private final ExpenseMappingService expenseMappingService;
    private final ChartJsServiceImpl chartJsServiceImpl;
    private final CardPeriodService cardPeriodService;

    public AccountManagementServiceImpl(GaliciaApiService galiciaApiService, ExpenseRepository expenseRepository, AccountRepository accountRepository, AlertEventService alertEventService, ExpenseMappingService expenseMappingService, ChartJsServiceImpl chartJsServiceImpl, CardPeriodService cardPeriodService) {
        this.galiciaApiService = galiciaApiService;
        this.expenseRepository = expenseRepository;
        this.accountRepository = accountRepository;
        this.alertEventService = alertEventService;
        this.expenseMappingService = expenseMappingService;
        this.chartJsServiceImpl = chartJsServiceImpl;
        this.cardPeriodService = cardPeriodService;
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
        // El redirect de selectAccount renueva la sesion: los cookies de aca en adelante son otros
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
            // Se descartan los que no se pueden sincronizar: moneda distinta de ARS o sin los datos minimos
            if (!movement.getMoneda().equals(GALICIA_CURRENCY_ARS_ID)) {
                log.info("[syncBankAccount] Se ignora el movimiento [{} {} {}] por moneda invalida: {}", DateUtils.format(movement.getFecha()), getDescription(movement), movement.getAmount(), movement.getMoneda());
                return false;
            } else if (movement.getFecha() == null) {
                log.info("[isValid] Invalid {}: fecha is null", movement);
                return false;
            } else if (movement.getDescripcionAMostrar() == null && movement.getDescripcionSide() == null) {
                log.info("[isValid] Invalid {}: descripcionAMostrar & descripcionSide is null", movement);
                return false;
            } else if (movement.getAmount() == null) {
                log.info("[isValid] Invalid {}: amount is null", movement);
                return false;
            }

            // Me fijo si alguno de los gastos existentes ya corresponde a este movimiento del Galicia
            String bankDesc = getDescription(movement);
            List<Expense> expensesByDateAndAmount = expenseRepository.findByAccountAndDateAndAmountEquals(account, movement.getFecha(), movement.getAmount());
            for (Expense expense : expensesByDateAndAmount) {
                if (expensesIdFounded.contains(expense.getId())) {
                    continue;
                }

                // Si hay varios gastos con la misma cuenta, fecha e importe, me fijo que la descripcion original (la que baja el banco y no se puede modificar desde la app) coincida, para no consumir un gasto que corresponde a otro movimiento
                if (!descriptionMatches(expense, bankDesc)) {
                    continue;
                }

                if (!StringUtils.hasText(expense.getOriginalDescription())) {
                    expense.setOriginalDescription(bankDesc);
                    expenseRepository.save(expense);
                    log.info("[syncBankAccount] Actualizado originalDescription del gasto {}: {}", expense.getId(), bankDesc);
                }

                expensesIdFounded.add(expense.getId());
                return false;
            }

            // Si no aparece con la fecha del movimiento, puede estar imputado al dia habil anterior: el banco no postea fines de semana ni feriados
            LocalDate date = movement.getFecha();
            boolean isWorkingDay = false;
            while (!isWorkingDay) {
                date = date.minusDays(1);
                if (DateUtils.isWeekend(date) || DateUtils.esFeriado(date)) {
                    expensesByDateAndAmount = expenseRepository.findByAccountAndDateAndAmountEquals(account, date, movement.getAmount());
                    for (Expense expense : expensesByDateAndAmount) {
                        if (expensesIdFounded.contains(expense.getId())) {
                            continue;
                        }

                        if (!descriptionMatches(expense, bankDesc)) {
                            continue;
                        }

                        if (!StringUtils.hasText(expense.getOriginalDescription())) {
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

            return true;
        }).collect(Collectors.toList());

        log.info("[syncBankAccount] Luego de filtrar los movimientos de la cuenta {} {}", account.getName(), movements.isEmpty()
                ? "no me quedaron movimientos por sincronizar"
                : "me quedaron " + movements.size() + " movimientos por sincronizar");

        SyncResult syncResult = new SyncResult(account.getName());

        List<Expense> allExpensesInRange = expenseRepository.findByAccountAndDateBetween(account, from, to, Sort.by(Sort.Direction.DESC, "date", "id"));
        List<Expense> unmatchedDbExpenses = allExpensesInRange.stream()
                .filter(expense -> !expensesIdFounded.contains(expense.getId()))
                .filter(expense -> BigDecimal.ZERO.compareTo(expense.getAmount()) != 0)
                .toList();
        if (!unmatchedDbExpenses.isEmpty()) {
            log.info("Los siguientes gastos de la cuenta {} no fueron encontrados al sincronizar con el banco:", account.getName());
            for (Expense unmatchedDbExpense : unmatchedDbExpenses) {
                log.info("  {} - {} {} {}", DateUtils.format(unmatchedDbExpense.getDate()), unmatchedDbExpense.getDescription(), unmatchedDbExpense.getDetails() != null ? "(" + unmatchedDbExpense.getDetails() + ")" : "", unmatchedDbExpense.getAmount());
                syncResult.getUnmatchedDbExpenses().add(DateUtils.format(unmatchedDbExpense.getDate()) + " - " + unmatchedDbExpense.getDescription() + (unmatchedDbExpense.getDetails() != null ? " (" + unmatchedDbExpense.getDetails() + ")" : "") + " " + unmatchedDbExpense.getAmount());
            }
        }

        if (movements.isEmpty()) {
            markAccountAsSynced(account);
            return CommonResult.ok(syncResult, syncResult.toHtmlMessage());
        }

        final List<Expense> expensesCreated = new ArrayList<>();
        for (int i = movements.size() - 1; i >= 0; i--) {
            BankAccountMovement bankAccountMovement = movements.get(i);
            expensesCreated.add(createExpense(account.getOwner(), bankAccountMovement.getFecha(), account, getDescription(bankAccountMovement), bankAccountMovement.getAmount()));
        }

        syncResult.setCreatedCount(movements.size());
        markAccountAsSynced(account);
        return CommonResult.ok(syncResult, syncResult.toHtmlMessage());
    }

    /**
     * Recorre los movimientos hacia atras en bloques de 3 meses y devuelve las descripciones que aparecen
     * mas de una vez y todavia no tienen un mapeo, para proponer nuevas reglas.
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
        // El redirect de selectAccount renueva la sesion: los cookies de aca en adelante son otros
        cuentasCookies = (String) selectResult.getPayload();

        final int monthsGap = 3;
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(monthsGap);
        CommonResult getMovimientosCuentaResult = CommonResult.ok();
        boolean hasMovements = true;

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
                    .filter(entry -> entry.getValue() > 1)
                    .filter(entry -> expenseMappingService.matchExpenseMapping(account.getOwner(), entry.getKey()).isEmpty())
                    .sorted(Comparator.comparing(Map.Entry<String, Integer>::getValue, Comparator.reverseOrder()).thenComparing(Map.Entry::getKey))
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
            cardPeriodService.syncFromGalicia(creditCardAccount, cookies);
            log.info("[syncCreditCardAccountMovements] Por consultar movimientos de la tarjeta {}", creditCardAccount.getName());
            CommonResult getCardMovementsResult = galiciaApiService.getCardMovements(cookies, creditCardBrand, galiciaAccountNumber, null, null);
            if (getCardMovementsResult.isError()) {
                return getCardMovementsResult;
            }

            List<Consumption> consumptions = (List<Consumption>) getCardMovementsResult.getPayload();
            if (CollectionUtils.isEmpty(consumptions)) {
                log.info("[syncCreditCardAccountMovements] No se recuperaron movimientos de la tarjeta de credito para sincronizar");
                markAccountAsSynced(creditCardAccount);
                SyncResult syncResult = new SyncResult(creditCardAccount.getName());
                syncResult.setCreatedCount(0);
                return CommonResult.ok(syncResult, "No se recuperaron movimientos de la tarjeta de credito");
            }

            log.info("[syncCreditCardAccountMovements] Se recuperaron {} movimientos de la tarjeta de credito. Se procede a filtrar los movimientos ya existentes", consumptions.size());

            // Se descartan los consumos que no tienen los datos minimos para sincronizar
            consumptions = consumptions.stream()
                    .filter(consumption -> {
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
                // Me fijo si el consumo ya existe entre los gastos de la tarjeta
                if (isQuota) {
                    log.debug("Por buscar gasto con cuota {} de {} por {}", consumption.getInstallmentNumber(), consumption.getInstallmentPlan(), consumption.getFinalAmount());
                    foundedExpenses = filterByAmount(
                            expenseRepository.findByAccountAndDetailsLike(creditCardAccount, "%Cuota " + consumption.getInstallmentNumber() + " de " + consumption.getInstallmentPlan() + "%"),
                            consumption.getFinalAmount());
                } else {
                    // Solo contemplo las fechas si el consumo es de este periodo (cuotas/devoluciones pueden tener como fecha de transaccion la fecha de compra)
                    if (consumption.getTransactionDate().isAfter(periodStart)) {
                        if (minTransactionDate == null || consumption.getTransactionDate().isBefore(minTransactionDate)) minTransactionDate = consumption.getTransactionDate();
                        if (maxTransactionDate == null || consumption.getTransactionDate().isAfter(maxTransactionDate))  maxTransactionDate = consumption.getTransactionDate();
                    }
                    log.debug("Por buscar gasto del {} por {}. Consumption.desc: {}", DateUtils.format(consumption.getTransactionDate()), consumption.getFinalAmount(), consumption.getMerchantName());
                    foundedExpenses = filterByAmount(
                            expenseRepository.findByAccountAndDate(creditCardAccount, consumption.getTransactionDate()),
                            consumption.getFinalAmount());
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

                    if (expense.getPeriod() == null) {
                        final CardPeriod period = resolvePeriodForConsumption(creditCardAccount, consumption);
                        if (period != null) {
                            expense.setPeriod(period);
                            expenseRepository.save(expense);
                            log.info("[syncCreditCardAccountMovements] Asignado periodo {} al gasto {}: {}", period.getClosingDate(), expense.getId(), consumption.getMerchantName());
                        }
                    }

                    expensesIdFounded.add(expense.getId());
                    found = true;
                    break;
                }

                // No aparece en la DB: se crea despues del loop
                if (!found) {
                    if (isQuota) {
                        log.info("[syncCreditCardAccountMovements] Cuota {} de {} por {} no encontrada, se crea el Expense", consumption.getInstallmentNumber(), consumption.getInstallmentPlan(), consumption.getFinalAmount());
                    }
                    consumptionsToCreate.add(consumption);
                }
            }

            log.info("[syncCreditCardAccountMovements] Luego de filtrar los movimientos de la tarjeta de credito {}", consumptionsToCreate.isEmpty()
                    ? "no me quedaron movimientos por sincronizar"
                    : "me quedaron " + consumptionsToCreate.size() + " movimientos por sincronizar");

            final List<Expense> expensesCreated;
            if (consumptionsToCreate.isEmpty()) {
                expensesCreated = new ArrayList<>();
            } else {
                expensesCreated = consumptionsToCreate.stream().map(consumption -> {
                    final CardPeriod period = resolvePeriodForConsumption(creditCardAccount, consumption);
                    final String details = consumption.getInstallmentPlan() != null && consumption.getInstallmentPlan() > 0 && consumption.getInstallmentNumber() != null
                            ? "Cuota " + consumption.getInstallmentNumber() + " de " + consumption.getInstallmentPlan()
                            : null;
                    final Expense previousInstallment = details != null ? findPreviousInstallment(creditCardAccount, consumption) : null;
                    return createExpense(creditCardAccount.getOwner(), consumption.getTransactionDate(), creditCardAccount, consumption.getMerchantName(), consumption.getFinalAmount(), period, details, previousInstallment);
                }).toList();
                expensesCreated.forEach(expenseCreated -> expensesIdFounded.add(expenseCreated.getId()));
            }

            final CardPeriod openPeriod = cardPeriodService.findOpenPeriod(creditCardAccount);
            final List<Expense> creditCardAccountExpensesToCheck = openPeriod != null
                    ? expenseRepository.findByAccountAndPeriod(creditCardAccount, openPeriod)
                    : expenseRepository.findByAccountAndDateBetween(creditCardAccount, minTransactionDate, maxTransactionDate, Sort.by(Sort.Direction.DESC, "date", "id"));
            // Los pagos de tarjeta no vienen en la API de consumos: no son movimientos faltantes
            List<Expense> expensesNotFoundInConsuptions = creditCardAccountExpensesToCheck.stream()
                    .filter(expense -> !expensesIdFounded.contains(expense.getId()) && expense.getTags().stream().noneMatch(tag -> "Pago de tarjeta".equals(tag.getName())))
                    .filter(expense -> BigDecimal.ZERO.compareTo(expense.getAmount()) != 0)
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

            SyncResult syncResult = new SyncResult(creditCardAccount.getName());
            syncResult.setCreatedCount(expensesCreated.size());
            for (Expense expense : expensesNotFoundInConsuptions) {
                syncResult.getUnmatchedDbExpenses().add(DateUtils.format(expense.getDate()) + " - " + expense.getDescription() + (StringUtils.hasText(expense.getDetails()) ? " (" + expense.getDetails() + ")" : "") + " " + expense.getAmount());
            }

            markAccountAsSynced(creditCardAccount);
            return CommonResult.ok(syncResult, syncResult.toHtmlMessage());
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

    private String movementStableKey(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }

        // El banco puede reordenar/variar los campos de la cola (CBU, medios de pago, numeros de referencia), pero los primeros campos
        // (header, nombre del tercero, CUIT/DNI) son estables para la misma transferencia. Los primeros 3 alcanzan para identificar el movimiento.
        String[] segments = description.split("<br\\s*/?>");
        List<String> stableFields = new ArrayList<>();
        for (String segment : segments) {
            String normalized = segment.replaceAll("[^a-zA-Z0-9\\u00C0-\\u024F]", " ")
                    .trim()
                    .replaceAll("\\s+", " ")
                    .toLowerCase();
            if (StringUtils.hasText(normalized)) {
                stableFields.add(normalized);
            }
            if (stableFields.size() == 3) {
                break;
            }
        }

        return String.join("|", stableFields);
    }

    private boolean descriptionMatches(Expense expense, String bankDesc) {
        if (!StringUtils.hasText(expense.getOriginalDescription())) {
            return true;
        }

        return movementStableKey(expense.getOriginalDescription()).equals(movementStableKey(bankDesc));
    }

    private CardPeriod resolvePeriodForConsumption(Account creditCardAccount, Consumption consumption) {
        if (consumption.getInstallmentPlan() != null && consumption.getInstallmentPlan() > 0) {
            final CardPeriod periodFromPreviousInstallment = resolvePeriodFromPreviousInstallment(creditCardAccount, consumption);
            if (periodFromPreviousInstallment != null) {
                return periodFromPreviousInstallment;
            }

            final CardPeriod openPeriod = cardPeriodService.findOpenPeriod(creditCardAccount);
            if (openPeriod != null) {
                log.warn("[resolvePeriodForConsumption] No se pudo deducir el periodo de la cuota {} de {} de {} desde la cuota anterior. Se usa el periodo en curso {}", consumption.getInstallmentNumber(), consumption.getInstallmentPlan(), consumption.getMerchantName(), openPeriod.getClosingDate());
                return openPeriod;
            }

            log.warn("[resolvePeriodForConsumption] No se pudo resolver el periodo de la cuota {} de {} de {}. El gasto queda sin periodo", consumption.getInstallmentNumber(), consumption.getInstallmentPlan(), consumption.getMerchantName());
            return null;
        }

        final LocalDate periodDate = consumption.getSubmissionDate() != null ? consumption.getSubmissionDate() : consumption.getTransactionDate();
        return cardPeriodService.findPeriodForDate(creditCardAccount, periodDate);
    }

    private Expense findPreviousInstallment(Account creditCardAccount, Consumption consumption) {
        return findPreviousInstallmentCandidates(creditCardAccount, consumption).stream()
                .max(Comparator.comparing(Expense::getDate, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private List<Expense> findPreviousInstallmentCandidates(Account creditCardAccount, Consumption consumption) {
        if (consumption.getInstallmentNumber() == null || consumption.getInstallmentNumber() <= 1) {
            return new ArrayList<>();
        }

        final String previousDetails = "Cuota " + (consumption.getInstallmentNumber() - 1) + " de " + consumption.getInstallmentPlan();
        final List<Expense> candidates = expenseRepository.findByAccountAndDetailsLike(creditCardAccount, "%" + previousDetails + "%");
        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }

        final List<Expense> sameMerchant = candidates.stream()
                .filter(previous -> descriptionMatchesConsumption(previous, consumption))
                .collect(Collectors.toList());
        return sameMerchant.isEmpty() ? candidates : sameMerchant;
    }

    private CardPeriod resolvePeriodFromPreviousInstallment(Account creditCardAccount, Consumption consumption) {
        return findPreviousInstallmentCandidates(creditCardAccount, consumption).stream()
                .filter(previous -> previous.getPeriod() != null && previous.getPeriod().getClosingDate() != null)
                .max(Comparator.comparing((Expense previous) -> previous.getPeriod().getClosingDate()))
                .map(previous -> cardPeriodService.findNextPeriod(creditCardAccount, previous.getPeriod()))
                .orElse(null);
    }

    private boolean descriptionMatchesConsumption(Expense expense, Consumption consumption) {
        final String merchantName = consumption.getMerchantName();
        if (!StringUtils.hasText(merchantName)) {
            return false;
        }

        return merchantName.equalsIgnoreCase(expense.getDescription()) || merchantName.equalsIgnoreCase(expense.getOriginalDescription());
    }

    private List<Expense> filterByAmount(List<Expense> expenses, BigDecimal amount) {
        return expenses.stream()
                .filter(expense -> expense.getAmount() != null && expense.getAmount().subtract(amount).abs().compareTo(AMOUNT_TOLERANCE) <= 0)
                .collect(Collectors.toList());
    }

    private Expense createExpense(User user, LocalDate date, Account account, String bankDescription, BigDecimal amount) {
        return createExpense(user, date, account, bankDescription, amount, null, null, null);
    }

    private Expense createExpense(User user, LocalDate date, Account account, String bankDescription, BigDecimal amount, CardPeriod period, String details, Expense previousInstallment) {
        Expense expense = new Expense();
        expense.setUser(user);
        expense.setDate(date);
        expense.setAccount(account);
        expense.setAmount(amount);
        expense.setOriginalDescription(bankDescription);
        if (previousInstallment != null) {
            log.info("[createExpense] La cuota hereda descripcion, detalle, tags y comentarios de la cuota anterior {}", previousInstallment.getId());
            expense.setDescription(previousInstallment.getDescription());
            expense.setDetails(resolveInheritedDetails(previousInstallment.getDetails(), details));
            expense.setTags(previousInstallment.getTags() != null ? new ArrayList<>(previousInstallment.getTags()) : new ArrayList<>());
            expense.setComments(previousInstallment.getComments());
        } else {
            final Optional<ExpenseMapping> mapping = expenseMappingService.matchExpenseMapping(user, bankDescription);
            if (mapping.isPresent()) {
                expense.setDescription(expenseMappingService.resolveNormalizedDescription(mapping.get(), bankDescription));
                expense.setDetails(StringUtils.hasText(details) ? details : expenseMappingService.resolveDetails(mapping.get(), bankDescription));
                expense.setTags(mapping.get().getTags() != null ? new ArrayList<>(mapping.get().getTags()) : new ArrayList<>());
            } else {
                expense.setDescription(bankDescription);
                expense.setDetails(StringUtils.hasText(details) ? details : null);
                expense.setTags(new ArrayList<>());
            }
        }

        expense.setDescription(resolveCompleteDescription(account, expense.getDescription()));
        expense.setPeriod(period);

        expense = expenseRepository.save(expense);
        log.info("[createExpense] Expense created: {} {} {}", DateUtils.format(expense.getDate()), expense.getDescription(), expense.getAmount());
        alertEventService.saveExpenseAlert(EntityEvent.CREATED, expense.getId(), "", user.getId());
        return expense;
    }

    private String resolveInheritedDetails(String previousDetails, String details) {
        if (!StringUtils.hasText(details)) {
            return null;
        }
        if (!StringUtils.hasText(previousDetails)) {
            return details;
        }

        final Matcher matcher = INSTALLMENT_DETAILS_PATTERN.matcher(previousDetails.trim());
        if (!matcher.find()) {
            return details;
        }

        return previousDetails.trim().substring(0, matcher.start()) + details;
    }

    private String resolveCompleteDescription(Account account, String description) {
        if (!StringUtils.hasText(description)) {
            return description;
        }

        String completeDescription = expenseRepository.findByAccountAndDescriptionStartingWith(account, description).stream()
                .map(Expense::getDescription)
                .filter(candidate -> candidate.length() > description.length())
                .max(Comparator.comparingInt(String::length))
                .orElse(description);

        if (!completeDescription.equals(description)) {
            log.info("[createExpense] Usando descripcion completa '{}' en lugar de '{}'", completeDescription, description);
        }
        return completeDescription;
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
