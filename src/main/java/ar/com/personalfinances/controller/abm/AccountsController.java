package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.controller.ApplicationController;
import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.AccountType;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.service.SpecificationsService;
import ar.com.personalfinances.util.AccountSearch;
import ar.com.personalfinances.util.ApplicationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Controller
public class AccountsController {

    private final AccountRepository accountRepository;
    private final SpecificationsService specificationsService;

    public AccountsController(AccountRepository accountRepository, SpecificationsService specificationsService) {
        this.accountRepository = accountRepository;
        this.specificationsService = specificationsService;
    }

    // TODO: Falta migrarla y que use mas un modal de Bootstrap

    @RequestMapping("/accounts")
    public String getAccountsPage(Model model, @ModelAttribute AccountSearch accountSearch,
                                  @RequestParam("page") Optional<Integer> page,
                                  @RequestParam("size") Optional<Integer> size,
                                  @RequestParam("accountIdToEdit") Optional<Long> accountIdToEdit) {
        int currentPage = page.orElse(ApplicationController.DEFAULT_PAGE_INDEX);
        int pageSize = size.orElse(ApplicationController.DEFAULT_PAGE_SIZE);

        accountSearch.setOwnerId(ApplicationUtils.getUserFromSession().getId());

        List<Account> accounts = accountRepository.findAll(specificationsService.getAccounts(accountSearch), Sort.by(Sort.Direction.ASC, "type", "name"));
        Page<Account> accountsPage = ApplicationController.getItemsPaginated(PageRequest.of(currentPage - 1, pageSize), accounts);

        model.addAttribute("accountsPage", accountsPage);

        int totalPages = accountsPage.getTotalPages();
        if (totalPages > 0) {
            List<Integer> pageNumbers = IntStream.rangeClosed(1, totalPages).boxed().collect(Collectors.toList());
            model.addAttribute("pageNumbers", pageNumbers);
        }

        if (accountIdToEdit.isPresent()) {
            model.addAttribute("account", accountRepository.findById(accountIdToEdit.get()).orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountIdToEdit)));
            model.addAttribute("accountIdToEdit", accountIdToEdit);
        } else {
            Account account = new Account();
            account.setOwner(ApplicationUtils.getUserFromSession());
            model.addAttribute("account", account);
        }
        model.addAttribute("accountSearch", accountSearch);
        model.addAttribute("module", "accounts");
        model.addAttribute("accountTypes", AccountType.values());
        return "abm/accounts";
    }

    @PostMapping("/account/add")
    public String postAddAccount(@Valid Account account, BindingResult result, Model model,
                                 @ModelAttribute AccountSearch accountSearch,
                                 @RequestParam("page") Optional<Integer> page,
                                 @RequestParam("size") Optional<Integer> size,
                                 @RequestParam("accountIdToEdit") Optional<Long> accountIdToEdit) {
        if (result.hasErrors()) {
            return getAccountsPage(model, accountSearch, page, size, accountIdToEdit);
        }

        accountRepository.save(account);
        return "redirect:/accounts";
    }

    @PostMapping("/account/update/{id}")
    public String updateAccount(@PathVariable("id") long id, @Valid Account account, BindingResult result, Model model,
                                @ModelAttribute AccountSearch accountSearch,
                                @RequestParam("page") Optional<Integer> page,
                                @RequestParam("size") Optional<Integer> size,
                                @RequestParam("accountIdToEdit") Optional<Long> accountIdToEdit) {
        if (result.hasErrors()) {
            account.setId(id);
            return getAccountsPage(model, accountSearch, page, size, accountIdToEdit);
        }

        accountRepository.save(account);

        return "redirect:/accounts";
    }

    @GetMapping("/account/delete/{id}")
    public String deleteAccount(@PathVariable("id") long id) {
        Account user = accountRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
        accountRepository.delete(user);

        return "redirect:/accounts";
    }
}