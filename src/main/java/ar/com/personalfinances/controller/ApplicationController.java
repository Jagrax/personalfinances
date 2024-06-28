package ar.com.personalfinances.controller;

import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.*;
import ar.com.personalfinances.service.SpecificationsService;
import ar.com.personalfinances.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
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
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final ReportsRepository reportsRepository;
    private final SpecificationsService specificationsService;

    @Autowired
    public ApplicationController(ExpenseRepository expenseRepository, CategoryRepository categoryRepository, AccountRepository accountRepository, ReportsRepository reportsRepository, SpecificationsService specificationsService) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.reportsRepository = reportsRepository;
        this.specificationsService = specificationsService;
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