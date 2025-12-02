package ar.com.personalfinances.controller;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.Category;
import ar.com.personalfinances.entity.Expense;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.CategoryRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.repository.ReportsRepository;
import ar.com.personalfinances.service.PDFService;
import ar.com.personalfinances.service.SpecificationsService;
import ar.com.personalfinances.util.AccountSearch;
import ar.com.personalfinances.util.ApplicationUtils;
import ar.com.personalfinances.util.CategorySearch;
import ar.com.personalfinances.util.ExpenseSearch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final PDFService pdfService;

    @Autowired
    public ApplicationController(ExpenseRepository expenseRepository, CategoryRepository categoryRepository, AccountRepository accountRepository, ReportsRepository reportsRepository, SpecificationsService specificationsService, PDFService pdfService) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.reportsRepository = reportsRepository;
        this.specificationsService = specificationsService;
        this.pdfService = pdfService;
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

    @PostMapping("/pdf/upload")
    public ResponseEntity<String> uploadPdf(@RequestParam("file") MultipartFile file) {
        try {
            final String pdfAsText = pdfService.extractText(file);
            final StringBuilder sb = new StringBuilder("<table border=\"1\">");

            if (StringUtils.hasText(pdfAsText)) {
                Pattern pattern = Pattern.compile(
                        "^"
                                + "(\\d{2}-[A-Za-z]{3}-\\d{2})"            // fecha
                                + "\\s+"
                                + "(.+?)"                 // descripción
                                + "(?:\\s+(\\d{2})/(\\d{2}))?"             // cuota opcional NN/NN
                                + "(?:\\s+(\\d{3,}))?"                     // referencia opcional
                                + "\\s+(-?[0-9.,]+)"                       // monto principal
                                + "(?:\\s+(-?[0-9.,]+))?"                  // segundo monto opcional
                                + "$"
                );

                final String datePattern = "dd-MMM-yy";
                SimpleDateFormat sdf = new SimpleDateFormat(datePattern, new Locale("es"));
                boolean startReading = false;
                boolean areCuotas = false;
                List<Account> userAccounts = getUserAccounts(new AccountSearch(), Sort.by(Sort.Direction.ASC,"name"));
                Account account = userAccounts.stream().filter(userAccount -> userAccount.getName().equals("Master Card")).collect(Collectors.toList()).get(0);
                Date minDate = null, maxDate = null;
                List<Expense> expensesFromPDF = new ArrayList<>();
                for (String textRow : pdfAsText.split("\n")) {
                    if (textRow == null) continue;
                    String row = textRow.trim();              // quita espacios alrededor
                    if (row.isEmpty()) continue;

                    if (startReading) {
                        Matcher matcher = pattern.matcher(row);
                        if (matcher.find()) {
                            String description = matcher.group(2).trim();

                            // descartar pagos en dólares
                            if (description.contains("U$S")) continue;

                            String amount = matcher.group(6).replace(".", "").replace(",", ".");         // 13.600,00
                            Date date;
                            try {
                                date = sdf.parse(matcher.group(1));
                            } catch (ParseException e) {
                                try {
                                    date = new SimpleDateFormat(datePattern, Locale.ENGLISH).parse(matcher.group(1));
                                } catch (ParseException e2) {
                                    throw new IllegalArgumentException("Fecha inválida: " + e2);
                                }
                            }

                            Expense expenseFromPDF = new Expense();
                            expenseFromPDF.setDate(date);
                            expenseFromPDF.setDescription(description);
                            expenseFromPDF.setAmount(new BigDecimal(amount));
                            String quotaNum = matcher.group(3);
                            String quotaDen = matcher.group(4);
                            if (quotaNum != null && quotaDen != null) {
                                expenseFromPDF.setDetails("Cuota " + Integer.parseInt(quotaNum) + " de " + Integer.parseInt(quotaDen));
                            }
                            expensesFromPDF.add(expenseFromPDF);

                            // La fecha minima no la quiero calcular a partir de los gastos de cuotas
                            if (!areCuotas) {
                                if (minDate == null) {
                                    minDate = date;
                                } else if (date.before(minDate)) {
                                    minDate = date;
                                }
                            }

                            if (maxDate == null) {
                                maxDate = date;
                            } else if (date.after(maxDate)) {
                                maxDate = date;
                            }
                        } else if (row.equals("CUOTA DEL MES")) {
                            areCuotas = true;
                        }
                    } else {
                        startReading = row.startsWith("CONSOLIDADO");
                    }
                }

                SimpleDateFormat sdf2 = new SimpleDateFormat("dd/MM/yyyy");
                List<Expense> allExpenses = expenseRepository.findByAccountAndDateBetween(account, minDate, maxDate, Sort.by(Sort.Direction.DESC, "date", "id"));
                List<Expense> expensesFromPDFMatched = new ArrayList<>();
                for (Expense expense : allExpenses) {
                    List<Expense> foundedExpenses = expensesFromPDF.stream()
                            .filter(expenseFromPDF -> {
                                // Distinto importe
                                if (!expenseFromPDF.getAmount().equals(expense.getAmount())) {
                                    return false;
                                }

                                // Es un gasto normal, descarto si no coincide la fecha
                                if (expenseFromPDF.getDetails() != null) {
                                    // Es un gasto en cuotas, si coincide el nro de cuota y el total, lo tomo como valido
                                    return expense.getDetails().contains(expenseFromPDF.getDetails());
                                } else {
                                    return expenseFromPDF.getDate().equals(expense.getDate());
                                }
                            })
                            .collect(Collectors.toList());
                    sb.append("<tr>");
                    int foundedExpensesCount = foundedExpenses.size();
                    String rowspan = foundedExpensesCount > 1 ? " rowspan=\"" + foundedExpensesCount + "\"" : "";
                    sb.append("<td ").append(rowspan).append(">").append(sdf2.format(expense.getDate())).append("</td>");
                    sb.append("<td ").append(rowspan).append(">").append(expense.getDescription()).append("</td>");
                    sb.append("<td ").append(rowspan).append(" class=\"text-end\">").append(expense.getAmount()).append("</td>");
                    if (foundedExpenses.size() > 1) {
                        for (Expense foundedExpense : foundedExpenses) {
                            sb.append("<td>").append(sdf2.format(foundedExpense.getDate())).append("</td>");
                            sb.append("<td>").append(foundedExpense.getDescription()).append("</td>");
                            sb.append("<td class=\"text-end\">").append(foundedExpense.getAmount()).append("</td>");
                            sb.append("</tr><tr>");
                        }
                    } else if (foundedExpenses.size() == 1) {
                        Expense foundedExpense = foundedExpenses.get(0);
                        sb.append("<td>").append(sdf2.format(foundedExpense.getDate())).append("</td>");
                        sb.append("<td>").append(foundedExpense.getDescription()).append("</td>");
                        sb.append("<td class=\"text-end\">").append(foundedExpense.getAmount()).append("</td>");
                    }
                    expensesFromPDFMatched.addAll(foundedExpenses);
                    sb.append("</tr>");
                }

                for (Expense expense : expensesFromPDF) {
                    if (!expensesFromPDFMatched.contains(expense)) {
                        sb.append("<tr><td colspan=\"3\"></td>");
                        sb.append("<td>").append(sdf2.format(expense.getDate())).append("</td>");
                        sb.append("<td>").append(expense.getDescription()).append("</td>");
                        sb.append("<td class=\"text-end\">").append(expense.getAmount()).append("</td>");
                        sb.append("</tr>");
                    }
                }
            }
            sb.append("</table>");

            return ResponseEntity.ok(sb.toString());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}