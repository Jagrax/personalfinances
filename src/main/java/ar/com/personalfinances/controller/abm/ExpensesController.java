package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.controller.ApplicationController;
import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.CategoryRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.service.AlertEventService;
import ar.com.personalfinances.service.SpecificationsService;
import ar.com.personalfinances.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

    public ExpensesController(ExpenseRepository expenseRepository, AlertEventService alertEventService, SpecificationsService specificationsService, CategoryRepository categoryRepository, AccountRepository accountRepository) {
        this.expenseRepository = expenseRepository;
        this.alertEventService = alertEventService;
        this.specificationsService = specificationsService;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
    }

    @RequestMapping("/expenses")
    public String getExpensesPage(Model model,
                                  @ModelAttribute ExpenseSearch expenseSearch,
                                  @RequestParam("page") Optional<Integer> page,
                                  @RequestParam("size") Optional<Integer> size,
                                  @RequestParam("accountType") Optional<String> accountType,
                                  @RequestParam("accountName") Optional<String> accountName) {
        int currentPage = page.orElse(ApplicationController.DEFAULT_PAGE_INDEX);
        int pageSize = size.orElse(ApplicationController.DEFAULT_PAGE_SIZE);

        AccountSearch accountSearch = new AccountSearch();

        // Intento recuperar el Usuario logueado
        User user = ApplicationUtils.getUserFromSession();

        // Por defecto, quiero ver siempre mis gastos
        expenseSearch.setUserId(user.getId());

        // Si me vino el tipo de cuenta, lo uso para filtrar
        accountType.ifPresent(s -> {
            AccountType accountTypeEnum = AccountType.valueOf(s);
            expenseSearch.setAccountType(accountTypeEnum);
            accountSearch.setAccountType(accountTypeEnum);
        });
        // Y si me vino un accountName, lo uso para filtrar
        accountName.ifPresent(s -> {
            expenseSearch.setAccountName(s);
            accountSearch.setName(s);
        });

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
        model.addAttribute("categories", getUserCategories(new CategorySearch(), Sort.by(Sort.Direction.ASC,"name")));
        // Cuentas que se muestran en el filtro Cuentas
        List<Account> accounts = getUserAccounts(accountSearch, Sort.by(Sort.Direction.ASC,"name"));
        model.addAttribute("accounts", accounts);
        if (accounts.size() == 1) expenseSearch.setAccountId(accounts.iterator().next().getId());
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

    @RequestMapping(value = "/expenses/create", method = RequestMethod.GET)
    public String createExpense(Model model, @RequestParam("backUrl") Optional<String> backUrl) {
        Expense expense = new Expense();
        expense.setUser(ApplicationUtils.getUserFromSession());
        expense.setCategory(categoryRepository.findById(Category.GENERIC_CATEGORY_ID).orElseThrow(() -> new ResourceNotFoundException("Category", "id", Category.GENERIC_CATEGORY_ID)));
        expense.setDate(new Date());
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