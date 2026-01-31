package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.AccountType;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.service.AccountManagementService;
import ar.com.personalfinances.service.ApplicationMessageService;
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

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Controller
public class BankSyncController {

    private final SpecificationsService specificationsService;
    private final AccountRepository accountRepository;
    private final ApplicationMessageService applicationMessageService;
    private final AccountManagementService accountManagementService;

    public BankSyncController(SpecificationsService specificationsService, AccountRepository accountRepository, ApplicationMessageService applicationMessageService, AccountManagementService accountManagementService) {
        this.specificationsService = specificationsService;
        this.accountRepository = accountRepository;
        this.applicationMessageService = applicationMessageService;
        this.accountManagementService = accountManagementService;
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
}