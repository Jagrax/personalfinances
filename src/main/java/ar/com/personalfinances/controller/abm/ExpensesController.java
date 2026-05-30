package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.controller.ApplicationController;
import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.CategoryRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.service.AlertEventService;
import ar.com.personalfinances.service.ExpenseService;
import ar.com.personalfinances.service.SpecificationsService;
import ar.com.personalfinances.util.*;
import ar.com.personalfinances.web.model.BulkExpenseUpdateRequest;
import ar.com.personalfinances.web.model.FilterChip;
import ar.com.personalfinances.web.model.FilterOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Controller
public class ExpensesController {

    private final ExpenseRepository expenseRepository;
    private final AlertEventService alertEventService;
    private final SpecificationsService specificationsService;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final ExpenseService expenseService;

    public ExpensesController(ExpenseRepository expenseRepository, AlertEventService alertEventService, SpecificationsService specificationsService, CategoryRepository categoryRepository, AccountRepository accountRepository, ExpenseService expenseService) {
        this.expenseRepository = expenseRepository;
        this.alertEventService = alertEventService;
        this.specificationsService = specificationsService;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.expenseService = expenseService;
    }

    @RequestMapping("/expenses")
    public String getExpensesPage(Model model,
                                  @ModelAttribute ExpenseSearch expenseSearch,
                                  @RequestParam("page") Optional<Integer> page,
                                  @RequestParam("size") Optional<Integer> size,
                                  @RequestParam("categoryName") Optional<String> categoryName,
                                  @RequestParam("accountType") Optional<String> accountType,
                                  @RequestParam("accountName") Optional<String> accountName,
                                  @RequestParam("accountId") Optional<Long> accountId) {
        int currentPage = page.orElse(ApplicationController.DEFAULT_PAGE_INDEX);
        int pageSize = size.orElse(ApplicationController.DEFAULT_PAGE_SIZE);

        // Intento recuperar el Usuario logueado
        User user = ApplicationUtils.getUserFromSession();

        // Por defecto, quiero ver siempre mis gastos
        expenseSearch.setUserId(user.getId());

        // Si me vino el tipo de cuenta, lo uso para filtrar
        accountType.ifPresent(s -> expenseSearch.setAccountType(AccountType.valueOf(s)));
        // Y si me vino un accountName, lo uso para filtrar
        accountName.ifPresent(expenseSearch::setAccountName);
        // Y si me vino un accountName, lo uso para filtrar
        accountId.ifPresent(expenseSearch::setAccountId);

        List<Category> userCategories = getUserCategories(new CategorySearch(), Sort.by(Sort.Direction.ASC,"name"));
        categoryName.ifPresent(s -> {
            expenseSearch.setCategoryName(s);
            for (Category userCategory : userCategories) {
                if (s.equals(userCategory.getName())) {
                    expenseSearch.setCategoryId(userCategory.getId());
                    break;
                }
            }
        });

        // Me traigo las expenses ordenadas por fecha y id desc y las paso por el paginador
        ExpensePage expensesPage = getExpensesPaginated(PageRequest.of(currentPage - 1, pageSize), expenseRepository.findAll(specificationsService.getExpenses(expenseSearch), Sort.by(Sort.Direction.DESC, "date", "id")));
        // Agrego la pagina de expensas que tengo que dibujar en pantalla
        model.addAttribute("expensesPage", expensesPage);
        model.addAttribute("hasPrevious", expensesPage.hasPrevious());
        model.addAttribute("hasNext", expensesPage.hasNext());
        model.addAttribute("currentPage", currentPage);

        final int totalPages = expensesPage.getTotalPages();
        model.addAttribute("totalPages", totalPages);
        if (totalPages > 0) {
            int startPage = Math.max(1, currentPage - 10);
            int endPage = Math.min(totalPages, currentPage + 10);

            List<Integer> pageNumbers = IntStream.rangeClosed(startPage, endPage)
                    .boxed()
                    .collect(Collectors.toList());

            model.addAttribute("pageNumbers", pageNumbers);
        }

        // Categorias que se muestran en el filtro de Categorias
        model.addAttribute("categories", userCategories);
        // Cuentas disponibles para editar/duplicar registros.
        List<Account> userAccounts = getUserAccounts(new AccountSearch(), Sort.by(Sort.Direction.ASC,"name"));
        model.addAttribute("accounts", userAccounts);
        if (userAccounts.size() == 1) expenseSearch.setAccountId(userAccounts.iterator().next().getId());
        // Atributo usado para settear la clase 'active' en el item del menu que corresponda
        String module = "expenses";
        if (accountType.isPresent()) {
            module += "-" + accountType.get().toLowerCase();
        }
        model.addAttribute("module", module);

        List<FilterChip> filterChips = expenseSearch.getActiveFilters();
        for (FilterChip chip : filterChips) {
            if ("categoryId".equals(chip.getField())) userCategories.stream().filter(category -> category.getId().equals(Long.valueOf(chip.getValue()))).findFirst().ifPresent(category -> chip.setValue(category.getName()));
            if ("accountId".equals(chip.getField())) userAccounts.stream().filter(account -> account.getId().equals(Long.valueOf(chip.getValue()))).findFirst().ifPresent(account -> chip.setValue(account.getName()));
        }
        model.addAttribute("filterChips", filterChips);

        // Pojo que contiene los valores de los filtros utilizados para obtener el conjunto de expenses
        model.addAttribute("expenseSearch", expenseSearch);
        model.addAttribute("stringFilterOperators", List.of(FilterOperator.CONTAINS, FilterOperator.EQ, FilterOperator.EMPTY, FilterOperator.NOT_EMPTY));
        return "abm/expenses";
    }

    @RequestMapping(value = "/expenses/create", method = RequestMethod.GET)
    public String createExpense(Model model, @RequestParam("backUrl") Optional<String> backUrl) {
        Expense expense = new Expense();
        expense.setUser(ApplicationUtils.getUserFromSession());
        expense.setCategory(categoryRepository.findById(Category.GENERIC_CATEGORY_ID).orElseThrow(() -> new ResourceNotFoundException("Category", "id", Category.GENERIC_CATEGORY_ID)));
        expense.setDate(LocalDate.now());
        backUrl.ifPresent(urlString -> {
            if (urlString.contains("?") && (urlString.contains("accountType") || urlString.contains("accountName"))) {
                URI url;
                try {
                    url = new URI(urlString);
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }

                // Obtener la cadena de consulta (query)
                String query = url.getQuery();

                // Decodificar la cadena de consulta
                String decodedQuery = URLDecoder.decode(query, StandardCharsets.UTF_8);
                if (!decodedQuery.isEmpty()) {
                    Map<String, String> paramsMap = new HashMap<>();
                    for (String param : decodedQuery.split("&")) {
                        String[] keyValue = param.split("=");
                        if (keyValue.length == 2) {
                            String key = keyValue[0];
                            String value = keyValue[1];
                            paramsMap.put(key, value);
                        }
                    }

                    String accountType = null;
                    if (paramsMap.containsKey("accountType")) {
                        accountType = paramsMap.get("accountType");
                    }

                    String accountName = null;
                    if (paramsMap.containsKey("accountName")) {
                        accountName = paramsMap.get("accountName");
                    }

                    AccountSearch accountSearch = new AccountSearch();
                    if (StringUtils.hasText(accountType)) {
                        accountSearch.setAccountType(AccountType.valueOf(accountType));
                    }

                    if (StringUtils.hasText(accountName)) {
                        accountSearch.setName(accountName);
                    }

                    User user = ApplicationUtils.getUserFromSession();
                    accountSearch.setOwnerIds(Collections.singletonList(user.getId()));
                    List<Account> accounts = accountRepository.findAll(specificationsService.getAccounts(accountSearch));
                    if (!accounts.isEmpty()) {
                        expense.setAccount(accounts.iterator().next());
                    }
                }
            }
        });
        return getExpensesEditPage(model, expense, backUrl);
    }

    @PostMapping(value = "/expenses/save-ajax")
    @ResponseBody
    public void createOrUpdateExpenseAjax(@RequestBody Expense expense) {
        User user = ApplicationUtils.getUserFromSession();
        expense.setUser(user);
        expenseService.saveWithAudit(expense, user);
    }

    @PostMapping("/expenses/bulk-update")
    @ResponseBody
    public void bulkUpdateExpenses(@RequestBody BulkExpenseUpdateRequest request) {
        User user = ApplicationUtils.getUserFromSession();
        expenseService.bulkUpdateExpenses(request, user);
    }

    @PostMapping("/expenses/delete-ajax/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpenseAjax(@PathVariable("id") long id) {
        Expense expense = expenseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Expense", "id", id));
        expenseRepository.delete(expense);
        alertEventService.saveExpenseAlert(EntityEvent.DELETED, expense.getId(), "", ApplicationUtils.getUserFromSession().getId());
    }

    private String getExpensesEditPage(Model model, Expense expense, Optional<String> backUrl) {
        model.addAttribute("expense", expense);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("accounts", getUserAccounts(new AccountSearch(), Sort.by(Sort.Direction.ASC,"name")));
        // Atributo usado para settear la clase 'active' en el item del menu que corresponda
        model.addAttribute("module", "expenses");

        backUrl.ifPresent(s -> model.addAttribute("backUrl", s));
        return "abm/expenses-edit";
    }

    public ExpensePage getExpensesPaginated(Pageable pageable, List<Expense> expensesToPaginate) {
        int pageSize = pageable.getPageSize();
        int currentPage = pageable.getPageNumber();
        int startItem = currentPage * pageSize;
        List<Expense> list;

        if (expensesToPaginate.size() < startItem) {
            list = Collections.emptyList();
        } else {
            int toIndex = Math.min(startItem + pageSize, expensesToPaginate.size());
            list = expensesToPaginate.subList(startItem, toIndex);
        }

        return new ExpensePage(list, pageable, expensesToPaginate.size(),
                expensesToPaginate.stream().collect(Collectors.groupingBy(
                        expense -> expense.getAccount() != null && expense.getAccount().getCurrency() != null? expense.getAccount().getCurrency() : "ARS",
                        Collectors.mapping(Expense::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)))),
                list.stream().collect(Collectors.groupingBy(
                        expense -> expense.getAccount() != null && expense.getAccount().getCurrency() != null? expense.getAccount().getCurrency() : "ARS",
                        Collectors.mapping(Expense::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)))));
    }

    private List<Account> getUserAccounts(AccountSearch accountSearch, Sort sort) {
        List<Long> accountSearchOwnerIds = new ArrayList<>();
        accountSearchOwnerIds.add(-1L); // La cuenta Generica la pueden utilizar todos los usuarios

        User user = ApplicationUtils.getUserFromSession(false);
        if (user != null) {
            // Si no tengo al usuario, no puedo ver ninguna cuenta mas que la -1
            accountSearchOwnerIds.add(user.getId());
        }

        accountSearch.setOwnerIds(accountSearchOwnerIds);
        return accountRepository.findAll(specificationsService.getAccounts(accountSearch), sort);
    }

    private List<Category> getUserCategories(CategorySearch categorySearch, Sort sort) {
        List<Long> categorySearchOwnerIds = new ArrayList<>();
        categorySearchOwnerIds.add(-1L); // La cuenta Generica la pueden utilizar todos los usuarios

        User user = ApplicationUtils.getUserFromSession(false);
        if (user != null) {
            // Si no tengo al usuario, no puedo ver ninguna cuenta mas que la -1
            categorySearchOwnerIds.add(user.getId());
        }

        categorySearch.setOwnerIds(categorySearchOwnerIds);
        return categoryRepository.findAll(specificationsService.getCategories(categorySearch), sort);
    }
}
