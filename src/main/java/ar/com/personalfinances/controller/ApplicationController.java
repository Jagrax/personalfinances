package ar.com.personalfinances.controller;

import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.CategoryRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.repository.ReportsRepository;
import ar.com.personalfinances.service.AlertEventService;
import ar.com.personalfinances.service.SpecificationsService;
import ar.com.personalfinances.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Controller
public class ApplicationController {

    private final int DEFAULT_PAGE_INDEX = 1;
    private final int DEFAULT_PAGE_SIZE = 10;

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final ReportsRepository reportsRepository;
    private final SpecificationsService specificationsService;
    private final AlertEventService alertEventService;

    @Autowired
    public ApplicationController(ExpenseRepository expenseRepository, CategoryRepository categoryRepository, AccountRepository accountRepository, ReportsRepository reportsRepository, SpecificationsService specificationsService, AlertEventService alertEventService) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.reportsRepository = reportsRepository;
        this.specificationsService = specificationsService;
        this.alertEventService = alertEventService;
    }

    // ------------------------- EXPENSES -------------------------

    @RequestMapping("/expenses")
    public String getExpensesPage(Model model,
                                  @ModelAttribute ExpenseSearch expenseSearch,
                                  @RequestParam("page") Optional<Integer> page,
                                  @RequestParam("size") Optional<Integer> size,
                                  @RequestParam("accountType") Optional<String> accountType,
                                  @RequestParam("accountName") Optional<String> accountName) {
        int currentPage = page.orElse(DEFAULT_PAGE_INDEX);
        int pageSize = size.orElse(DEFAULT_PAGE_SIZE);

        // Intento recuperar el Usuario logueado
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (user == null) {
            throw new RuntimeException("No se pudo recuperar el usuario asociado a authentication.principal");
        }

        // Por defecto, quiero ver siempre mis gastos
        expenseSearch.setUserId(user.getId());

        // Si me vino el tipo de cuenta, lo uso para filtrar
        accountType.ifPresent(s -> expenseSearch.setAccountType(AccountType.valueOf(s)));
        // Y si me vino un accountName, lo uso para filtrar
        accountName.ifPresent(expenseSearch::setAccountName);

        // Me traigo las expenses ordenadas por fecha y id desc y las paso por el paginador
        ExpensePage expensesPage = getExpensesPaginated(PageRequest.of(currentPage - 1, pageSize), expenseRepository.findAll(specificationsService.getExpenses(expenseSearch), Sort.by(Sort.Direction.DESC, "date", "id")));
        // Agrego la pagina de expensas que tengo que dibujar en pantalla
        model.addAttribute("expensesPage", expensesPage);
        // Si tengo 1 pagina o mas, entonces calculo los numero de paginas del paginador. Estos se dibujan al pie de la tabla de expenses
        int totalPages = expensesPage.getTotalPages();
        if (totalPages > 0) {
            List<Integer> pageNumbers = IntStream.rangeClosed(1, totalPages).boxed().collect(Collectors.toList());
            model.addAttribute("pageNumbers", pageNumbers);
        }

        // Categorias que se muestran en el filtro de Categorias
        model.addAttribute("categories", categoryRepository.findAll());
        // Cuentas que se muestran en el filtro Cuentas
        model.addAttribute("accounts", getUserAccounts(new AccountSearch(), Sort.by(Sort.Direction.ASC,"name")));
        // Atributo usado para settear la clase 'active' en el item del menu que corresponda
        String module = "expenses";
        if (accountType.isPresent()) {
            module += "-" + accountType.get().toLowerCase();
        }
        model.addAttribute("module", module);
        // Pojo que contiene los valores de los filtros utilizados para obtener el conjunto de expenses
        model.addAttribute("expenseSearch", expenseSearch);
        return "abm/expenses";
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

    @RequestMapping(value = "/expenses/create", method = RequestMethod.GET)
    public String createExpense(Model model, @RequestParam("backUrl") Optional<String> backUrl) {
        Expense expense = new Expense();
        expense.setUser(ApplicationUtils.getUserFromSession());
        expense.setCategory(categoryRepository.findById(Category.GENERIC_CATEGORY_ID).orElseThrow(() -> new ResourceNotFoundException("Category", "id", Category.GENERIC_CATEGORY_ID)));
        expense.setDate(new Date());

        return getExpensesEditPage(model, expense, backUrl);
    }

    @RequestMapping(value = "/expenses/edit", method = RequestMethod.GET)
    public String editExpense(Model model, @RequestParam(name = "expenseIdToEdit") Optional<Long> expenseIdToEdit, @RequestParam("backUrl") Optional<String> backUrl) {
        Optional<Expense> expenseToEdit;
        if (expenseIdToEdit.isPresent()) {
            expenseToEdit = expenseRepository.findById(expenseIdToEdit.get());
            if (expenseToEdit.isPresent()) {
                User user = ApplicationUtils.getUserFromSession();
                if (expenseToEdit.get().getUser().getId().equals(user.getId())) {
                    return getExpensesEditPage(model, expenseToEdit.get(), backUrl);
                } else {
                    model.addAttribute("applicationMessage", ApplicationMessage.warn("El gasto que esta intentado editar no le pertenece"));
                }
            } else {
                model.addAttribute("applicationMessage", ApplicationMessage.warn("El gasto que esta intentado editar no existe"));
            }
        } else {
            model.addAttribute("applicationMessage", ApplicationMessage.warn("No se especifico el id del gasto a editar"));
        }

        return createExpense(model, backUrl);
    }

    @RequestMapping(value = "/expenses/duplicate", method = RequestMethod.GET)
    public String duplicateExpense(Model model, @RequestParam(name = "cloneExpenseId") Optional<Long> cloneExpenseId, @RequestParam("backUrl") Optional<String> backUrl) {
        Optional<Expense> expenseToClone;
        if (cloneExpenseId.isPresent()) {
            expenseToClone = expenseRepository.findById(cloneExpenseId.get());
            if (expenseToClone.isPresent()) {
                User user = ApplicationUtils.getUserFromSession();
                if (expenseToClone.get().getUser().getId().equals(user.getId())) {
                    Expense expense = ApplicationUtils.cloneEntity(expenseToClone.get(), true);
                    expense.setUser(user);
                    return getExpensesEditPage(model, expense, backUrl);
                } else {
                    model.addAttribute("applicationMessage", ApplicationMessage.warn("El gasto que esta intentado clonar no le pertenece"));
                }
            } else {
                model.addAttribute("applicationMessage", ApplicationMessage.warn("El gasto que esta intentado clonar no existe"));
            }
        } else {
            model.addAttribute("applicationMessage", ApplicationMessage.warn("No se especifico el id del gasto del cual clonar"));
        }

        return createExpense(model, backUrl);
    }

    @RequestMapping(value = "/expenses/save", method = RequestMethod.POST)
    public String createOrUpdateExpense(Model model, @Valid Expense expense, BindingResult result, @RequestParam("backUrl") Optional<String> backUrl) {
        if (result.hasErrors()) {
            model.addAttribute("applicationMessage", ApplicationMessage.error("Error de datos en el registro"));
            model.addAttribute("bindingResult", result);

            model.addAttribute("expense", expense);
            model.addAttribute("categories", categoryRepository.findAll());
            model.addAttribute("accounts", getUserAccounts(new AccountSearch(), Sort.by(Sort.Direction.ASC,"name")));
            // Atributo usado para settear la clase 'active' en el item del menu que corresponda
            model.addAttribute("module", "expenses");
            return "abm/expenses-edit";
        }

        EntityEvent event = expense.getId() == null ? EntityEvent.CREATED : EntityEvent.UPDATED;
        String eventDetails = "";
        if (event.equals(EntityEvent.UPDATED)) {
            eventDetails = ApplicationUtils.getChangeLog(expense, expenseRepository.findById(expense.getId()).orElseThrow());
        }
        expense = expenseRepository.save(expense);
        alertEventService.saveExpenseAlert(event, expense.getId(), eventDetails, ApplicationUtils.getUserFromSession().getId());

        if (backUrl.isPresent() && StringUtils.hasLength(backUrl.get())) {
            return "redirect:" + backUrl.get();
        }

        return "redirect:/expenses";
    }

    @GetMapping("/expenses/delete/{id}")
    public String deleteExpense(@PathVariable("id") long id, Optional<String> backUrl) {
        Expense expense = expenseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Expense", "id", id));
        expenseRepository.delete(expense);
        alertEventService.saveExpenseAlert(EntityEvent.DELETED, expense.getId(), "", ApplicationUtils.getUserFromSession().getId());

        if (backUrl.isPresent() && StringUtils.hasLength(backUrl.get())) {
            return "redirect:" + backUrl.get();
        }

        return "redirect:/expenses";
    }

    @RequestMapping("/expenses/report")
    public String getExpensesReportPage(Model model) {
        // Intento recuperar el Usuario logueado
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (user == null) {
            throw new RuntimeException("No se pudo recuperar el usuario asociado a authentication.principal");
        }

        // Por defecto, quiero ver siempre mis gastos
        List<Expense> expenses = expenseRepository.findByUser(user, Sort.by("date", "description"));
        List<String[]> serviciosReport = new ArrayList<>(Collections.singleton(new String[]{
                "Fecha", "Descripcion", "Importe", "Detalles", "Comentarios", "Categoria", "Cuenta"
        }));
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM");
        serviciosReport.addAll(expenses.stream().map(e -> new String[]{
                sdf.format(e.getDate()), e.getDescription(), e.getAmount().abs().toString(), e.getDetails(), e.getComments(), e.getCategory().getName(), e.getAccount().getName()
        }).collect(Collectors.toList()));
        model.addAttribute("serviciosReport", serviciosReport);

        // Categorias que se muestran en el filtro de Categorias
        model.addAttribute("categories", categoryRepository.findAll());
        // Cuentas que se muestran en el filtro Cuentas
        model.addAttribute("accounts", getUserAccounts(new AccountSearch(), Sort.by(Sort.Direction.ASC,"name")));
        return "reports/report";
    }

    // -------------------------  ACCOUNTS  -------------------------

    @RequestMapping("/accounts")
    public String getAccountsPage(Model model, @ModelAttribute AccountSearch accountSearch,
                                  @RequestParam("page") Optional<Integer> page,
                                  @RequestParam("size") Optional<Integer> size,
                                  @RequestParam("accountIdToEdit") Optional<Long> accountIdToEdit) {
        int currentPage = page.orElse(DEFAULT_PAGE_INDEX);
        int pageSize = size.orElse(DEFAULT_PAGE_SIZE);

        accountSearch.setOwnerId(ApplicationUtils.getUserFromSession().getId());

        List<Account> accounts = accountRepository.findAll(specificationsService.getAccounts(accountSearch), Sort.by(Sort.Direction.ASC, "type", "name"));
        Page<Account> accountsPage = getItemsPaginated(PageRequest.of(currentPage - 1, pageSize), accounts);

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

    // ------------------------- CATEGORIES -------------------------

    @RequestMapping("/categories")
    public String getCategoriesPage(Model model,
                                    @RequestParam("page") Optional<Integer> page,
                                    @RequestParam("size") Optional<Integer> size,
                                    @RequestParam("filterCategoryName") Optional<String> categoryName,
                                    @RequestParam("categoryIdToEdit") Optional<Long> categoryIdToEdit) {
        int currentPage = page.orElse(DEFAULT_PAGE_INDEX);
        int pageSize = size.orElse(DEFAULT_PAGE_SIZE);

        List<Category> categories;
        if (categoryName.isPresent() && StringUtils.hasText(categoryName.get())) {
            categories = categoryRepository.findByNameContainsIgnoreCase(categoryName.get());
        } else {
            categories = categoryRepository.findAll();
        }
        Page<Category> categoriesPage = getItemsPaginated(PageRequest.of(currentPage - 1, pageSize), categories);

        model.addAttribute("categoriesPage", categoriesPage);

        int totalPages = categoriesPage.getTotalPages();
        if (totalPages > 0) {
            List<Integer> pageNumbers = IntStream.rangeClosed(1, totalPages).boxed().collect(Collectors.toList());
            model.addAttribute("pageNumbers", pageNumbers);
        }

        if (categoryIdToEdit.isPresent()) {
            model.addAttribute("category", categoryRepository.findById(categoryIdToEdit.get()).orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryIdToEdit)));
            model.addAttribute("categoryIdToEdit", categoryIdToEdit);
        } else {
            model.addAttribute("category", new Category());
        }
        model.addAttribute("filterCategoryName", categoryName);
        model.addAttribute("module", "categories");
        return "abm/categories";
    }

    @PostMapping("/category/add")
    public String postAddCategory(@Valid Category category, BindingResult result, Model model,
                                  @RequestParam("page") Optional<Integer> page,
                                  @RequestParam("size") Optional<Integer> size,
                                  @RequestParam("filterCategoryName") Optional<String> categoryName,
                                  @RequestParam("categoryIdToEdit") Optional<Long> categoryIdToEdit) {
        if (result.hasErrors()) {
            return getCategoriesPage(model, page, size, categoryName, categoryIdToEdit);
        }

        categoryRepository.save(category);
        return "redirect:/categories";
    }

    @PostMapping("/category/update/{id}")
    public String updateCategory(@PathVariable("id") long id, @Valid Category category, BindingResult result, Model model,
                                 @RequestParam("page") Optional<Integer> page,
                                 @RequestParam("size") Optional<Integer> size,
                                 @RequestParam("filterCategoryName") Optional<String> filterCategoryName,
                                 @RequestParam("categoryIdToEdit") Optional<Long> categoryIdToEdit) {
        if (result.hasErrors()) {
            category.setId(id);
            return getCategoriesPage(model, page, size, filterCategoryName, categoryIdToEdit);
        }

        categoryRepository.save(category);

        return "redirect:/categories";
    }

    @GetMapping("/category/delete/{id}")
    public String deleteCategory(@PathVariable("id") long id) {
        Category user = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        categoryRepository.delete(user);

        return "redirect:/categories";
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
                                expense -> expense.getAccount().getCurrency(),
                                Collectors.mapping(Expense::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)))),
                list.stream().collect(Collectors.groupingBy(
                        expense -> expense.getAccount().getCurrency(),
                        Collectors.mapping(Expense::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)))));
    }

    public <T> Page<T> getItemsPaginated(Pageable pageable, List<T> itemsToPaginate) {
        int pageSize = pageable.getPageSize();
        int currentPage = pageable.getPageNumber();
        int startItem = currentPage * pageSize;
        List<T> list;

        if (itemsToPaginate.size() < startItem) {
            list = Collections.emptyList();
        } else {
            int toIndex = Math.min(startItem + pageSize, itemsToPaginate.size());
            list = itemsToPaginate.subList(startItem, toIndex);
        }

        return new PageImpl<>(list, PageRequest.of(currentPage, pageSize), itemsToPaginate.size());
    }

    @GetMapping({"/dashboard", "/"})
    public String getDashboardPage(Model model) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (user == null) {
            throw new RuntimeException("No se pudo recuperar el usuario asociado a authentication.principal");
        }

        model.addAttribute("accountsBalances", reportsRepository.getSumAmountsByAccount(user.getId()));
        return "dashboard";
    }

    @ResponseStatus(value = HttpStatus.CONFLICT, reason = "Data integrity violation")
    @ExceptionHandler(DataIntegrityViolationException.class)
    public void conflict() {
        // Nothing to do
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(HttpServletRequest req, Exception ex) {
        log.error("[" + ApplicationUtils.getCacheSafeValue() + "]:" + ex.getMessage());

        ModelAndView mav = new ModelAndView();
        mav.addObject("exception", ex);
        mav.addObject("url", req.getRequestURL());
        mav.setViewName("error/error");
        return mav;
    }

    private List<Account> getUserAccounts(AccountSearch accountSearch, Sort sort) {
        List<Long> accountSearchOwnerIds = new ArrayList<>();
        accountSearchOwnerIds.add(-1L); // La cuenta Generica la pueden utilizar todos los usuarios

        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (user != null) {
            // Si no tengo al usuario, no puedo ver ninguna cuenta mas que la -1
            accountSearchOwnerIds.add(user.getId());
        }

        accountSearch.setOwnerIds(accountSearchOwnerIds);
        return accountRepository.findAll(specificationsService.getAccounts(accountSearch), sort);
    }
}