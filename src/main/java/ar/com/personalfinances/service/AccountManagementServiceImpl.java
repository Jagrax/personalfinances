package ar.com.personalfinances.service;

import ar.com.personalfinances.api.galicia.model.Consumption;
import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.AccountApiCredentialsRepository;
import ar.com.personalfinances.repository.CategoryRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.util.CommonResult;
import ar.com.personalfinances.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccountManagementServiceImpl implements AccountManagementService {

    private final AccountApiCredentialsRepository accountApiCredentialsRepository;
    private final GaliciaApiService galiciaApiService;
    private final ExpenseRepository expenseRepository;
    private final AlertEventService alertEventService;
    private final ExpenseMappingService expenseMappingService;
    private final Category automaticCategory;

    public AccountManagementServiceImpl(AccountApiCredentialsRepository accountApiCredentialsRepository, GaliciaApiService galiciaApiService, ExpenseRepository expenseRepository, AlertEventService alertEventService, ExpenseMappingService expenseMappingService, CategoryRepository categoryRepository) {
        this.accountApiCredentialsRepository = accountApiCredentialsRepository;
        this.galiciaApiService = galiciaApiService;
        this.expenseRepository = expenseRepository;
        this.alertEventService = alertEventService;
        this.expenseMappingService = expenseMappingService;
        this.automaticCategory = categoryRepository.findById(Category.AUTOMATIC_CATEGORY_ID).orElseThrow(() -> new ResourceNotFoundException("Category", "id", Category.AUTOMATIC_CATEGORY_ID));
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

            final List<Long> expensesIdFounded = new ArrayList<>();
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
                        } else if (consumption.getInstallmentPlan() != null && consumption.getInstallmentPlan() != 0) {
                            log.info("[readCreditCardAccount] Se ignora el movimiento [{} {} {}] por ser una cuota: {} de {}", DateUtils.format(consumption.getTransactionDate()), consumption.getMerchantName(), consumption.getFinalAmount(), consumption.getInstallmentNumber(), consumption.getInstallmentPlan());
                            return false;
                        }

                        // Me fijo en los gastos existentes si alguno coincide con el que movimiento del Galicia
                        final List<Expense> expensesByDateAndAmount = expenseRepository.findByAccountAndDateAndAmountEquals(creditCardAccount, consumption.getTransactionDate(), consumption.getFinalAmount());
                        for (Expense expense : expensesByDateAndAmount) {
                            if (expensesIdFounded.contains(expense.getId())) {
                                continue;
                            }

                            expensesIdFounded.add(expense.getId());
                            return false;
                        }

                        // El gasto no existe en la DB y tiene los datos correctos. Lo guardo
                        return true;
                    })
                    .sorted(Comparator.comparing(Consumption::getTransactionDate))
                    .collect(Collectors.toList());

            log.info("[syncCreditCardAccountMovements] Luego de filtrar los movimientos de la tarjeta de credito {}", consumptions.isEmpty()
                    ? "no me quedaron movimientos por sincronizar"
                    : "me quedaron " + consumptions.size() + " movimientos por sincronizar");

            if (consumptions.isEmpty()) {
                return CommonResult.ok(consumptions, "Los gastos de la cuenta estan sincronizados!");
            }

            final List<Expense> expensesCreated = consumptions.stream().map(consumption -> createExpense(creditCardAccount.getOwner(), consumption.getTransactionDate(), creditCardAccount, consumption.getMerchantName(), consumption.getFinalAmount())).collect(Collectors.toList());
            return CommonResult.ok(expensesCreated, "Se sincronizaron " + consumptions.size() + " gastos en la cuenta");
        } else {
            throw new IllegalArgumentException("AccountAPICredentials.provider invalid [" + accountApiCredentials.getProvider() + "]");
        }
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
}