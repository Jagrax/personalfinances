package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.api.galicia.model.BankAccountMovement;
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
    private final AccountManagementService accountManagementService;

    public BankSyncController(SpecificationsService specificationsService, AccountRepository accountRepository, ExpenseRepository expenseRepository, AlertEventService alertEventService, CategoryRepository categoryRepository, GaliciaApiService galiciaApiService, ExpenseMappingService expenseMappingService, ApplicationMessageService applicationMessageService, AccountManagementService accountManagementService) {
        this.specificationsService = specificationsService;
        this.accountRepository = accountRepository;
        this.expenseRepository = expenseRepository;
        this.alertEventService = alertEventService;
        this.automaticCategory = categoryRepository.findById(Category.AUTOMATIC_CATEGORY_ID).orElseThrow(() -> new ResourceNotFoundException("Category", "id", Category.AUTOMATIC_CATEGORY_ID));
        this.galiciaApiService = galiciaApiService;
        this.expenseMappingService = expenseMappingService;
        this.applicationMessageService = applicationMessageService;
        this.accountManagementService = accountManagementService;
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

                    //learnFromBankMovements(bankSyncModelAttribute.getCookie(), account);

                    break;
                }
                default:
                    return "redirect:" + backUrl;
            }

            //noinspection SpringMVCViewInspection
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
            getMovimientosCuentaResult = galiciaApiService.getMovimientosCuenta(cookie, from, to);
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
}