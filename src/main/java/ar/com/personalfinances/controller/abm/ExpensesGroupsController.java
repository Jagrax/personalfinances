package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.controller.ApplicationController;
import ar.com.personalfinances.entity.EntityEvent;
import ar.com.personalfinances.entity.ExpensesGroup;
import ar.com.personalfinances.entity.Role;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.ExpensesGroupRepository;
import ar.com.personalfinances.repository.UserRepository;
import ar.com.personalfinances.service.AlertEventService;
import ar.com.personalfinances.util.ApplicationMessage;
import ar.com.personalfinances.util.ApplicationUtils;
import ar.com.personalfinances.util.ExpensesGroupSearch;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Controller
public class ExpensesGroupsController {

    private final ExpensesGroupRepository expensesGroupRepository;
    private final UserRepository userRepository;
    private final AlertEventService alertEventService;

    public ExpensesGroupsController(ExpensesGroupRepository expensesGroupRepository, UserRepository userRepository, AlertEventService alertEventService) {
        this.expensesGroupRepository = expensesGroupRepository;
        this.userRepository = userRepository;
        this.alertEventService = alertEventService;
    }

    @GetMapping("/expensesGroups")
    public String getExpensesGroupsPage(Model model,
                                        @ModelAttribute ExpensesGroupSearch expensesGroupSearch,
                                        @RequestParam("page") Optional<Integer> page,
                                        @RequestParam("size") Optional<Integer> size) {
        final int currentPage = page.orElse(ApplicationController.DEFAULT_PAGE_INDEX);
        final int pageSize = size.orElse(ApplicationController.DEFAULT_PAGE_SIZE);

        final List<ExpensesGroup> expensesGroups = getExpensesGroups(expensesGroupSearch, Sort.by(Sort.Direction.ASC, "name"));
        Page<ExpensesGroup> expensesGroupsPage = ApplicationController.getItemsPaginated(PageRequest.of(currentPage - 1, pageSize), expensesGroups);
        model.addAttribute("expensesGroupsPage", expensesGroupsPage);

        final int totalPages = expensesGroupsPage.getTotalPages();
        if (totalPages > 0) {
            final List<Integer> pageNumbers = IntStream.rangeClosed(1, totalPages).boxed().collect(Collectors.toList());
            model.addAttribute("pageNumbers", pageNumbers);
        }

        model.addAttribute("expensesGroupSearch", expensesGroupSearch);
        model.addAttribute("module", "expensesGroups");
        return "abm/expensesGroups";
    }

    @RequestMapping(value = "/expensesGroups/create", method = RequestMethod.GET)
    public String createExpensesGroup(Model model, @RequestParam("backUrl") Optional<String> backUrl) {
        ExpensesGroup expensesGroup = new ExpensesGroup();
        expensesGroup.setCreationUser(ApplicationUtils.getUserFromSession());
        return getExpensesGroupEditPage(model, expensesGroup, backUrl);
    }

    @RequestMapping(value = "/expensesGroups/edit", method = RequestMethod.GET)
    public String editExpensesGroup(Model model, @RequestParam(name = "expensesGroupIdToEdit") Optional<Long> expensesGroupIdToEdit, @RequestParam("backUrl") Optional<String> backUrl) {
        Optional<ExpensesGroup> expensesGroupToEdit;
        if (expensesGroupIdToEdit.isPresent()) {
            expensesGroupToEdit = expensesGroupRepository.findById(expensesGroupIdToEdit.get());
            if (expensesGroupToEdit.isPresent()) {
                ExpensesGroup expensesGroup = expensesGroupToEdit.get();
                User currentUser = ApplicationUtils.getUserFromSession();
                if (currentUser.getAuthorities().stream().anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(Role.ADMIN.name())) || expensesGroup.getCreationUser().equals(currentUser)) {
                    model.addAttribute("canEdit", true); // TODO: Implementar un esquema de ver/editar los datos del grupo segun si soy el dueño del mismo o si soy ADMIN
                    return getExpensesGroupEditPage(model, expensesGroupToEdit.get(), backUrl);
                } else if (expensesGroup.getMembers().contains(currentUser)) {
                    model.addAttribute("canEdit", false); // TODO: Implementar un esquema de ver/editar los datos del grupo segun si soy el dueño del mismo o si soy ADMIN
                    return getExpensesGroupEditPage(model, expensesGroupToEdit.get(), backUrl);
                } else {
                    model.addAttribute("applicationMessage", ApplicationMessage.warn("El grupo de gastos que esta intentado editar no le pertenece"));
                }
            } else {
                model.addAttribute("applicationMessage", ApplicationMessage.warn("El grupo de gastos que esta intentado editar no existe"));
            }
        } else {
            model.addAttribute("applicationMessage", ApplicationMessage.warn("No se especifico el id del grupo de gastos a editar"));
        }

        return createExpensesGroup(model, backUrl);
    }

    @PostMapping("/expensesGroups/save")
    public String createOrUpdateExpensesGroup(Model model, @Valid ExpensesGroup expensesGroup, BindingResult result, @RequestParam("backUrl") Optional<String> backUrl) {
        if (result.hasErrors()) {
            return getExpensesGroupEditPage(model, expensesGroup, backUrl);
        }

        // TODO: Ver la manera de que si estoy haciendo un create siempre controle, pero si es un update que ademas controle que no soy yo mismo el grupo con el mismo name/creationUser
        if (expensesGroup.getId() == null && expensesGroupRepository.existsByNameAndCreationUser(expensesGroup.getName(), expensesGroup.getCreationUser())) {
            result.rejectValue("name", "duplicate.group", "Ya existe un grupo con este nombre.");
            return getExpensesGroupEditPage(model, expensesGroup, backUrl);
        }

        expensesGroupRepository.save(expensesGroup);

        if (backUrl.isPresent() && StringUtils.hasLength(backUrl.get())) {
            return "redirect:" + backUrl.get();
        }

        return "redirect:/expensesGroups";
    }

    @GetMapping("/expensesGroups/delete/{id}")
    public String deleteExpensesGroup(@PathVariable("id") long id, Optional<String> backUrl) {
        ExpensesGroup expensesGroup = expensesGroupRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("ExpensesGroup", "id", id));
        expensesGroupRepository.delete(expensesGroup);
        alertEventService.saveExpenseAlert(EntityEvent.DELETED, expensesGroup.getId(), "", ApplicationUtils.getUserFromSession().getId());

        if (backUrl.isPresent() && StringUtils.hasLength(backUrl.get())) {
            return "redirect:" + backUrl.get();
        }

        return "redirect:/expensesGroups";
    }

    private List<ExpensesGroup> getExpensesGroups(ExpensesGroupSearch expensesGroupSearch, Sort sort) {
        // Obtengo los grupos filtrados en pantalla
        List<Long> expenseGroupSearchUserIds = expensesGroupSearch.getUserIds();
        if (expenseGroupSearchUserIds == null) expenseGroupSearchUserIds = new ArrayList<>();

        // Filtro para ver solamente los grupos a los que pertenezco y los que cree
        User user = ApplicationUtils.getUserFromSession();
        expenseGroupSearchUserIds.add(user.getId());
        expensesGroupSearch.setUserIds(expenseGroupSearchUserIds);

        // TODO: Falta implementar la busqueda de ExpensesGroups por el ExpensesGroupSearch
        return expensesGroupRepository.findAllByCreationUserOrMember(user, sort);
    }

    private String getExpensesGroupEditPage(Model model, ExpensesGroup expensesGroup, Optional<String> backUrl) {
        // Me aseguro de que no de NullPo
        if (expensesGroup.getMembers() == null) {
            expensesGroup.setMembers(new ArrayList<>());
        }
        model.addAttribute("expensesGroup", expensesGroup);
        model.addAttribute("users", userRepository.findAllByEnabledTrueAndLockedFalse());
        // Atributo usado para settear la clase 'active' en el item del menu que corresponda
        model.addAttribute("module", "expenses");

        backUrl.ifPresent(s -> model.addAttribute("backUrl", s));
        return "abm/expensesGroups-edit";
    }
}