package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.controller.ApplicationController;
import ar.com.personalfinances.entity.Category;
import ar.com.personalfinances.entity.ExpenseMapping;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.CategoryRepository;
import ar.com.personalfinances.repository.ExpenseMappingRepository;
import ar.com.personalfinances.service.SpecificationsService;
import ar.com.personalfinances.util.ApplicationUtils;
import ar.com.personalfinances.util.CategorySearch;
import ar.com.personalfinances.util.ExpenseMappingSearch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Controller
public class ExpenseMappingsController {

    private final ExpenseMappingRepository expenseMappingRepository;
    private final CategoryRepository categoryRepository;
    private final SpecificationsService specificationsService;

    public ExpenseMappingsController(ExpenseMappingRepository expenseMappingRepository, CategoryRepository categoryRepository, SpecificationsService specificationsService) {
        this.expenseMappingRepository = expenseMappingRepository;
        this.categoryRepository = categoryRepository;
        this.specificationsService = specificationsService;
    }

    @RequestMapping("/expenseMappings")
    public String getExpenseMappingsPage(Model model,
                                         @ModelAttribute ExpenseMappingSearch expenseMappingSearch,
                                         @RequestParam("page") Optional<Integer> page,
                                         @RequestParam("size") Optional<Integer> size,
                                         @RequestParam("expenseMappingIdToEdit") Optional<Long> expenseMappingIdToEdit) {
        int currentPage = page.orElse(ApplicationController.DEFAULT_PAGE_INDEX);
        int pageSize = size.orElse(ApplicationController.DEFAULT_PAGE_SIZE);
        User user = ApplicationUtils.getUserFromSession();

        expenseMappingSearch.setUserId(user.getId());

        List<ExpenseMapping> expenseMappings = expenseMappingRepository.findAll(
                specificationsService.getExpenseMappings(expenseMappingSearch),
                Sort.by(Sort.Direction.ASC, "normalizedDescription", "bankDescription", "id")
        );
        Page<ExpenseMapping> expenseMappingsPage = ApplicationController.getItemsPaginated(PageRequest.of(currentPage - 1, pageSize), expenseMappings);
        model.addAttribute("expenseMappingsPage", expenseMappingsPage);

        int totalPages = expenseMappingsPage.getTotalPages();
        if (totalPages > 0) {
            List<Integer> pageNumbers = IntStream.rangeClosed(1, totalPages).boxed().collect(Collectors.toList());
            model.addAttribute("pageNumbers", pageNumbers);
        }

        if (model.containsAttribute("expenseMapping")) {
            expenseMappingIdToEdit.ifPresent(id -> model.addAttribute("expenseMappingIdToEdit", expenseMappingIdToEdit));
        } else if (expenseMappingIdToEdit.isPresent()) {
            ExpenseMapping expenseMapping = expenseMappingRepository.findById(expenseMappingIdToEdit.get())
                    .orElseThrow(() -> new ResourceNotFoundException("ExpenseMapping", "id", expenseMappingIdToEdit.get()));
            assertCurrentUserOwns(expenseMapping, user);
            model.addAttribute("expenseMapping", expenseMapping);
            model.addAttribute("expenseMappingIdToEdit", expenseMappingIdToEdit);
        } else {
            model.addAttribute("expenseMapping", newExpenseMapping(user));
        }

        model.addAttribute("expenseMappingSearch", expenseMappingSearch);
        model.addAttribute("categories", getUserCategories(new CategorySearch(), Sort.by(Sort.Direction.ASC, "name")));
        model.addAttribute("module", "expenseMappings");
        return "abm/expenseMappings";
    }

    @PostMapping("/expenseMapping/add")
    public String addExpenseMapping(@Valid ExpenseMapping expenseMapping, BindingResult result, Model model,
                                    @ModelAttribute ExpenseMappingSearch expenseMappingSearch,
                                    @RequestParam("page") Optional<Integer> page,
                                    @RequestParam("size") Optional<Integer> size,
                                    @RequestParam("expenseMappingIdToEdit") Optional<Long> expenseMappingIdToEdit) {
        User user = ApplicationUtils.getUserFromSession();
        prepareExpenseMapping(expenseMapping, user);
        validateRegex(expenseMapping, result);

        if (result.hasErrors()) {
            model.addAttribute("expenseMapping", expenseMapping);
            model.addAttribute("openExpenseMappingModal", true);
            return getExpenseMappingsPage(model, expenseMappingSearch, page, size, expenseMappingIdToEdit);
        }

        expenseMappingRepository.save(expenseMapping);
        return "redirect:/expenseMappings";
    }

    @PostMapping("/expenseMapping/update/{id}")
    public String updateExpenseMapping(@PathVariable("id") long id, @Valid ExpenseMapping expenseMapping, BindingResult result, Model model,
                                       @ModelAttribute ExpenseMappingSearch expenseMappingSearch,
                                       @RequestParam("page") Optional<Integer> page,
                                       @RequestParam("size") Optional<Integer> size,
                                       @RequestParam("expenseMappingIdToEdit") Optional<Long> expenseMappingIdToEdit) {
        User user = ApplicationUtils.getUserFromSession();
        ExpenseMapping savedExpenseMapping = expenseMappingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExpenseMapping", "id", id));
        assertCurrentUserOwns(savedExpenseMapping, user);

        expenseMapping.setId(id);
        prepareExpenseMapping(expenseMapping, user);
        validateRegex(expenseMapping, result);

        if (result.hasErrors()) {
            model.addAttribute("expenseMapping", expenseMapping);
            model.addAttribute("openExpenseMappingModal", true);
            return getExpenseMappingsPage(model, expenseMappingSearch, page, size, expenseMappingIdToEdit);
        }

        expenseMappingRepository.save(expenseMapping);
        return "redirect:/expenseMappings";
    }

    @GetMapping("/expenseMapping/delete/{id}")
    public String deleteExpenseMapping(@PathVariable("id") long id) {
        User user = ApplicationUtils.getUserFromSession();
        ExpenseMapping expenseMapping = expenseMappingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExpenseMapping", "id", id));
        assertCurrentUserOwns(expenseMapping, user);

        expenseMappingRepository.delete(expenseMapping);
        return "redirect:/expenseMappings";
    }

    private ExpenseMapping newExpenseMapping(User user) {
        ExpenseMapping expenseMapping = new ExpenseMapping();
        expenseMapping.setUser(user);
        expenseMapping.setCreationDate(LocalDate.now());
        expenseMapping.setEnabled(true);
        return expenseMapping;
    }

    private void prepareExpenseMapping(ExpenseMapping expenseMapping, User user) {
        expenseMapping.setUser(user);
        if (expenseMapping.getCreationDate() == null) {
            expenseMapping.setCreationDate(LocalDate.now());
        }
        if (expenseMapping.getEnabled() == null) {
            expenseMapping.setEnabled(false);
        }
        if (expenseMapping.getCategory() != null && expenseMapping.getCategory().getId() == null) {
            expenseMapping.setCategory(null);
        }
    }

    private void validateRegex(ExpenseMapping expenseMapping, BindingResult result) {
        if (!StringUtils.hasText(expenseMapping.getRegexPattern())) {
            return;
        }

        try {
            Pattern.compile(expenseMapping.getRegexPattern());
        } catch (PatternSyntaxException e) {
            result.rejectValue("regexPattern", "invalid.regex", "Regex invalido: " + e.getMessage());
        }
    }

    private void assertCurrentUserOwns(ExpenseMapping expenseMapping, User user) {
        if (expenseMapping.getUser() == null || !expenseMapping.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("ExpenseMapping", "id", expenseMapping.getId());
        }
    }

    private List<Category> getUserCategories(CategorySearch categorySearch, Sort sort) {
        List<Long> categorySearchOwnerIds = new ArrayList<>();
        categorySearchOwnerIds.add(-1L);

        User user = ApplicationUtils.getUserFromSession(false);
        if (user != null) {
            categorySearchOwnerIds.add(user.getId());
        }

        categorySearch.setOwnerIds(categorySearchOwnerIds);
        return categoryRepository.findAll(specificationsService.getCategories(categorySearch), sort);
    }
}
