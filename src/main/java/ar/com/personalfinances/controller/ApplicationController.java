package ar.com.personalfinances.controller;

import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.*;
import ar.com.personalfinances.service.AlertEventService;
import ar.com.personalfinances.service.SpecificationsService;
import ar.com.personalfinances.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Controller
public class ApplicationController {

    public final static int DEFAULT_PAGE_INDEX = 1;
    public final static int DEFAULT_PAGE_SIZE = 10;

    private final ExpenseRepository expenseRepository;
    private final SharedExpenseRepository sharedExpenseRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final ReportsRepository reportsRepository;
    private final ExpensesGroupRepository expensesGroupRepository;
    private final SpecificationsService specificationsService;
    private final AlertEventService alertEventService;
    private final UserRepository userRepository;

    @Autowired
    public ApplicationController(ExpenseRepository expenseRepository, SharedExpenseRepository sharedExpenseRepository, CategoryRepository categoryRepository, AccountRepository accountRepository, ReportsRepository reportsRepository, SpecificationsService specificationsService, AlertEventService alertEventService, UserRepository userRepository, ExpensesGroupRepository expensesGroupRepository) {
        this.expenseRepository = expenseRepository;
        this.sharedExpenseRepository = sharedExpenseRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.reportsRepository = reportsRepository;
        this.expensesGroupRepository = expensesGroupRepository;
        this.specificationsService = specificationsService;
        this.alertEventService = alertEventService;
        this.userRepository = userRepository;
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

    @RequestMapping("/expenses/report")
    public String getExpensesReportPage(Model model, @ModelAttribute ExpenseSearch expenseSearch) {
        // Intento recuperar el Usuario logueado
        User user = ApplicationUtils.getUserFromSession();

        // Por defecto, quiero ver siempre mis gastos
        expenseSearch.setUserId(user.getId());

        // Por defecto, quiero ver siempre mis gastos
        List<Expense> expenses = expenseRepository.findAll(specificationsService.getExpenses(expenseSearch), Sort.by("date", "description"));
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
        // Pojo que contiene los valores de los filtros utilizados para obtener el conjunto de expenses
        model.addAttribute("expenseSearch", expenseSearch);
        return "reports/report";
    }

    // ------------------------- EXPENSES -------------------------

    @RequestMapping("/sharedExpenses")
    public String getSharedExpensesPage(Model model,
                                        @RequestParam("sharedExpensesGroupId") Optional<Long> sharedExpensesGroupId,
                                        @RequestParam("page") Optional<Integer> page,
                                        @RequestParam("size") Optional<Integer> size) {
        int currentPage = page.orElse(DEFAULT_PAGE_INDEX);
        int pageSize = size.orElse(25); // En SplitWise se muestran 25 items y el botón "Mostrat más"

        // Intento recuperar el Usuario logueado
        User user = ApplicationUtils.getUserFromSession();

        final Sort sort = Sort.by(Sort.Direction.DESC, "date", "id");
        List<SharedExpense> sharedExpenses;
        String sharedExpensesGroupTitle;
        if (sharedExpensesGroupId.isEmpty()) {
            sharedExpenses = sharedExpenseRepository.findAllByUserOrMember(user, sort);
            sharedExpensesGroupTitle = "Todos los gastos";
        } else {
            sharedExpenses = sharedExpenseRepository.findByExpensesGroupId(sharedExpensesGroupId.get(), sort);
            Optional<ExpensesGroup> expensesGroup = expensesGroupRepository.findById(sharedExpensesGroupId.get());
            if (expensesGroup.isPresent()) {
                sharedExpensesGroupTitle = expensesGroup.get().getName();
            } else {
                sharedExpensesGroupTitle = "";
            }
        }
        PageImpl<SharedExpense> sharedExpensePage = new PageImpl<>(sharedExpenses, PageRequest.of(currentPage - 1, pageSize), sharedExpenses.size());
        model.addAttribute("sharedExpensesPage", sharedExpensePage);
        model.addAttribute("sharedExpensesGroupTitle", sharedExpensesGroupTitle);

        // Si tengo 1 pagina o mas, entonces calculo los numero de paginas del paginador. Estos se dibujan al pie de la tabla de expenses
        int totalPages = sharedExpensePage.getTotalPages();
        if (totalPages > 0) {
            List<Integer> pageNumbers = IntStream.rangeClosed(1, totalPages).boxed().collect(Collectors.toList());
            model.addAttribute("pageNumbers", pageNumbers);
        }

        List<ExpensesGroup> expensesGroups = expensesGroupRepository.findAllByCreationUserOrMember(user, Sort.by(Sort.Direction.ASC, "name"));
        model.addAttribute("expensesGroups", expensesGroups.stream().filter(g -> g.getId() != -1).collect(Collectors.toList()));

        // Atributo usado para settear la clase 'active' en el item del menu que corresponda
        model.addAttribute("module", "expenses");
        return "abm/sharedExpenses";
    }

    private String getSharedExpensesEditPage(Model model, SharedExpense sharedExpense, Optional<String> backUrl) {
        model.addAttribute("sharedExpense", sharedExpense);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("users", userRepository.findAllByEnabledTrueAndLockedFalse());
        // Atributo usado para settear la clase 'active' en el item del menu que corresponda
        model.addAttribute("module", "expenses");
        List<ExpensesGroup> expensesGroups = expensesGroupRepository.findAll();
        model.addAttribute("expensesGroups", expensesGroups);

        backUrl.ifPresent(s -> model.addAttribute("backUrl", s));
        return "abm/sharedExpenses-edit";
    }

    @RequestMapping(value = "/sharedExpenses/create", method = RequestMethod.GET)
    public String createSharedExpense(Model model, @RequestParam("backUrl") Optional<String> backUrl) {
        SharedExpense sharedExpense = new SharedExpense();
        sharedExpense.setUser(ApplicationUtils.getUserFromSession());
        sharedExpense.setCategory(categoryRepository.findById(Category.GENERIC_CATEGORY_ID).orElseThrow(() -> new ResourceNotFoundException("Category", "id", Category.GENERIC_CATEGORY_ID)));
        sharedExpense.setDate(new Date());
        backUrl.ifPresent(urlString -> {
            if (urlString.contains("?") && urlString.contains("sharedExpensesGroupId")) {
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

                    String paramSharedExpensesGroupId = null;
                    if (paramsMap.containsKey("sharedExpensesGroupId")) {
                        paramSharedExpensesGroupId = paramsMap.get("sharedExpensesGroupId");
                    }

                    if (paramSharedExpensesGroupId != null) {
                        long sharedExpensesGroupId = Long.parseLong(paramSharedExpensesGroupId);
                        expensesGroupRepository.findById(sharedExpensesGroupId).ifPresent(expensesGroup -> {
                            User user = ApplicationUtils.getUserFromSession();
                            if (expensesGroup.getCreationUser().equals(user) || expensesGroup.getMembers().contains(user)) {
                                sharedExpense.setExpensesGroup(expensesGroup);
                                List<SharedExpenseMember> sharedExpenseMembers = new ArrayList<>();
                                for (User expensesGroupMember : expensesGroup.getMembers()) {
                                    SharedExpenseMember sharedExpenseMember = new SharedExpenseMember();
                                    sharedExpenseMember.setSharedExpense(sharedExpense);
                                    sharedExpenseMember.setUser(expensesGroupMember);
                                    sharedExpenseMember.setAmount(BigDecimal.ZERO);
                                    sharedExpenseMembers.add(sharedExpenseMember);
                                }
                                sharedExpense.setMembers(sharedExpenseMembers);
                            }
                        });
                    }
                }
            }
        });
        return getSharedExpensesEditPage(model, sharedExpense, backUrl);
    }

    @RequestMapping(value = "/sharedExpenses/edit", method = RequestMethod.GET)
    public String editSharedExpenses(Model model, @RequestParam(name = "sharedExpenseIdToEdit") Optional<Long> sharedExpenseIdToEdit, @RequestParam("backUrl") Optional<String> backUrl) {
        Optional<SharedExpense> sharedExpenseToEdit;
        if (sharedExpenseIdToEdit.isPresent()) {
            sharedExpenseToEdit = sharedExpenseRepository.findById(sharedExpenseIdToEdit.get());
            if (sharedExpenseToEdit.isPresent()) {
                return getSharedExpensesEditPage(model, sharedExpenseToEdit.get(), backUrl);
//                User user = ApplicationUtils.getUserFromSession();
//                if (sharedExpenseToEdit.get().getUser().getId().equals(user.getId())) {
//                    return getSharedExpensesEditPage(model, sharedExpenseToEdit.get(), backUrl);
//                } else {
//                    model.addAttribute("applicationMessage", ApplicationMessage.warn("El gasto que esta intentado editar no le pertenece"));
//                }
            } else {
                model.addAttribute("applicationMessage", ApplicationMessage.warn("El gasto que esta intentado editar no existe"));
            }
        } else {
            model.addAttribute("applicationMessage", ApplicationMessage.warn("No se especifico el id del gasto a editar"));
        }

        return createSharedExpense(model, backUrl);
    }

    @RequestMapping(value = "/sharedExpenses/duplicate", method = RequestMethod.GET)
    public String duplicateSharedExpense(Model model, @RequestParam(name = "cloneSharedExpenseId") Optional<Long> cloneSharedExpenseId, @RequestParam("backUrl") Optional<String> backUrl) {
        Optional<SharedExpense> sharedExpenseToClone;
        if (cloneSharedExpenseId.isPresent()) {
            sharedExpenseToClone = sharedExpenseRepository.findById(cloneSharedExpenseId.get());
            if (sharedExpenseToClone.isPresent()) {
                User user = ApplicationUtils.getUserFromSession();
                if (sharedExpenseToClone.get().getUser().getId().equals(user.getId())) {
                    SharedExpense expense = ApplicationUtils.cloneEntity(sharedExpenseToClone.get(), true);
                    expense.setUser(user);
                    return getSharedExpensesEditPage(model, expense, backUrl);
                } else {
                    model.addAttribute("applicationMessage", ApplicationMessage.warn("El gasto que esta intentado clonar no le pertenece"));
                }
            } else {
                model.addAttribute("applicationMessage", ApplicationMessage.warn("El gasto que esta intentado clonar no existe"));
            }
        } else {
            model.addAttribute("applicationMessage", ApplicationMessage.warn("No se especifico el id del gasto del cual clonar"));
        }

        return createSharedExpense(model, backUrl);
    }

    @RequestMapping(value = "/sharedExpenses/save", method = RequestMethod.POST)
    public String createOrUpdateSharedExpense(Model model, @Valid SharedExpense sharedExpense, BindingResult result, @RequestParam("backUrl") Optional<String> backUrl) {
        if (result.hasErrors()) {
            model.addAttribute("applicationMessage", ApplicationMessage.error("Error de datos en el registro"));
            model.addAttribute("bindingResult", result);

            model.addAttribute("sharedExpense", sharedExpense);
            model.addAttribute("categories", categoryRepository.findAll());
            model.addAttribute("users", userRepository.findAllByEnabledTrueAndLockedFalse());
            // Atributo usado para settear la clase 'active' en el item del menu que corresponda
            model.addAttribute("module", "sharedExpenses");
            return "abm/sharedExpenses-edit";
        }

        EntityEvent event = sharedExpense.getId() == null ? EntityEvent.CREATED : EntityEvent.UPDATED;
        String eventDetails = "";
        if (event.equals(EntityEvent.UPDATED)) {
            eventDetails = ApplicationUtils.getChangeLog(sharedExpense, sharedExpenseRepository.findById(sharedExpense.getId()).orElseThrow());
        }
        sharedExpense = sharedExpenseRepository.save(sharedExpense);
        alertEventService.saveSharedExpenseAlert(event, sharedExpense.getId(), eventDetails, ApplicationUtils.getUserFromSession().getId());

        if (backUrl.isPresent() && StringUtils.hasLength(backUrl.get())) {
            return "redirect:" + backUrl.get();
        }

        return "redirect:/sharedExpenses";
    }

    @GetMapping("/sharedExpenses/delete/{id}")
    public String deleteSharedExpense(@PathVariable("id") long id, Optional<String> backUrl) {
        SharedExpense sharedExpense = sharedExpenseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("SharedExpense", "id", id));
        sharedExpenseRepository.delete(sharedExpense);
        alertEventService.saveSharedExpenseAlert(EntityEvent.DELETED, sharedExpense.getId(), "", ApplicationUtils.getUserFromSession().getId());

        if (backUrl.isPresent() && StringUtils.hasLength(backUrl.get())) {
            return "redirect:" + backUrl.get();
        }

        return "redirect:/sharedExpenses";
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
                                    @ModelAttribute CategorySearch categorySearch,
                                    @RequestParam("page") Optional<Integer> page,
                                    @RequestParam("size") Optional<Integer> size,
                                    @RequestParam("categoryIdToEdit") Optional<Long> categoryIdToEdit) {
        int currentPage = page.orElse(DEFAULT_PAGE_INDEX);
        int pageSize = size.orElse(DEFAULT_PAGE_SIZE);

        categorySearch.setOwnerId(ApplicationUtils.getUserFromSession().getId());

        List<Category> categories = getUserCategories(categorySearch, Sort.by(Sort.Direction.ASC, "name"));
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
            Category category = new Category();
            category.setOwner(ApplicationUtils.getUserFromSession());
            model.addAttribute("category", category);
        }
        model.addAttribute("categorySearch", categorySearch);
        model.addAttribute("module", "categories");
        return "abm/categories";
    }

    @PostMapping("/category/add")
    public String postAddCategory(@Valid Category category, BindingResult result, Model model,
                                  @ModelAttribute CategorySearch categorySearch,
                                  @RequestParam("page") Optional<Integer> page,
                                  @RequestParam("size") Optional<Integer> size,
                                  @RequestParam("categoryIdToEdit") Optional<Long> categoryIdToEdit) {
        if (result.hasErrors()) {
            return getCategoriesPage(model, categorySearch, page, size, categoryIdToEdit);
        }

        categoryRepository.save(category);
        return "redirect:/categories";
    }

    @PostMapping("/category/update/{id}")
    public String updateCategory(@PathVariable("id") long id, @Valid Category category, BindingResult result, Model model,
                                 @ModelAttribute CategorySearch categorySearch,
                                 @RequestParam("page") Optional<Integer> page,
                                 @RequestParam("size") Optional<Integer> size,
                                 @RequestParam("categoryIdToEdit") Optional<Long> categoryIdToEdit) {
        if (result.hasErrors()) {
            category.setId(id);
            return getCategoriesPage(model, categorySearch, page, size, categoryIdToEdit);
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
                                expense -> expense.getAccount() != null && expense.getAccount().getCurrency() != null? expense.getAccount().getCurrency() : "ARS",
                                Collectors.mapping(Expense::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)))),
                list.stream().collect(Collectors.groupingBy(
                        expense -> expense.getAccount() != null && expense.getAccount().getCurrency() != null? expense.getAccount().getCurrency() : "ARS",
                        Collectors.mapping(Expense::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)))));
    }

    public static <T> Page<T> getItemsPaginated(Pageable pageable, List<T> itemsToPaginate) {
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
        model.addAttribute("accountsBalances", reportsRepository.getSumAmountsByAccount(ApplicationUtils.getUserFromSession().getId()));
        return "dashboard";
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