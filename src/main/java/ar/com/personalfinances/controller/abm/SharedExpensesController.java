package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.controller.ApplicationController;
import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.*;
import ar.com.personalfinances.service.AlertEventService;
import ar.com.personalfinances.util.ApplicationMessage;
import ar.com.personalfinances.util.ApplicationUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Controller
public class SharedExpensesController {

    private final SharedExpenseRepository sharedExpenseRepository;
    private final UserRepository userRepository;
    private final AlertEventService alertEventService;
    private final ExpensesGroupRepository expensesGroupRepository;
    private final CategoryRepository categoryRepository;
    private final SharedExpenseMemberRepository sharedExpenseMemberRepository;

    public SharedExpensesController(SharedExpenseRepository sharedExpenseRepository, UserRepository userRepository, AlertEventService alertEventService, ExpensesGroupRepository expensesGroupRepository, CategoryRepository categoryRepository, SharedExpenseMemberRepository sharedExpenseMemberRepository) {
        this.sharedExpenseRepository = sharedExpenseRepository;
        this.userRepository = userRepository;
        this.alertEventService = alertEventService;
        this.expensesGroupRepository = expensesGroupRepository;
        this.categoryRepository = categoryRepository;
        this.sharedExpenseMemberRepository = sharedExpenseMemberRepository;
    }

    @RequestMapping("/sharedExpenses")
    public String getSharedExpensesPage(Model model,
                                        @RequestParam("sharedExpensesGroupId") Optional<Long> sharedExpensesGroupId,
                                        @RequestParam("page") Optional<Integer> page,
                                        @RequestParam("size") Optional<Integer> size) {
        int currentPage = page.orElse(ApplicationController.DEFAULT_PAGE_INDEX);
        int pageSize = size.orElse(25); // En SplitWise se muestran 25 items y el botón "Mostrat más"

        // Intento recuperar el Usuario logueado
        User user = ApplicationUtils.getUserFromSession();

        final Sort sort = Sort.by(Sort.Direction.DESC, "date", "id");
        AtomicReference<List<SharedExpense>> sharedExpenses = new AtomicReference<>(new ArrayList<>());
        AtomicReference<String> sharedExpensesGroupTitle = new AtomicReference<>("");
        sharedExpensesGroupId.ifPresentOrElse(expensesGroupId -> {
            if (expensesGroupId.equals(-1L)) {
                sharedExpensesGroupTitle.set("Gastos sin grupo");
                sharedExpenses.set(sharedExpenseRepository.findAllWithoutGroupByUserOrMember(user, sort));
            } else {
                // sino, busco los gastos de ese grupo en particular, asegurandome de que sea uno creado por mi o del cual soy parte
                expensesGroupRepository.findById(expensesGroupId).ifPresent(expensesGroup -> {
                    if (expensesGroup.getCreationUser().equals(user) || (expensesGroup.getMembers() != null && expensesGroup.getMembers().contains(user))) {
                        sharedExpensesGroupTitle.set(expensesGroup.getName());
                        sharedExpenses.set(sharedExpenseRepository.findByExpensesGroupId(expensesGroupId, sort));
                    }
                });
            }
        }, () -> {
            // Traigo todos los gastos creados por mi o en los que participo
            sharedExpenses.set(sharedExpenseRepository.findAllByUserOrMember(user, sort));
            sharedExpensesGroupTitle.set("Todos los gastos");
        });

        PageImpl<SharedExpense> sharedExpensePage = new PageImpl<>(sharedExpenses.get(), PageRequest.of(currentPage - 1, pageSize), sharedExpenses.get().size());
        model.addAttribute("sharedExpensesPage", sharedExpensePage);
        model.addAttribute("sharedExpensesGroupTitle", sharedExpensesGroupTitle);

        // Si tengo 1 pagina o mas, entonces calculo los numero de paginas del paginador. Estos se dibujan al pie de la tabla de expenses
        int totalPages = sharedExpensePage.getTotalPages();
        if (totalPages > 0) {
            List<Integer> pageNumbers = IntStream.rangeClosed(1, totalPages).boxed().collect(Collectors.toList());
            model.addAttribute("pageNumbers", pageNumbers);
        }

        // Obtengo los grupos creados por mi o a los que pertenezco
        List<ExpensesGroup> expensesGroups = expensesGroupRepository.findAllByCreationUserOrMember(user, Sort.by(Sort.Direction.ASC, "name"));
        model.addAttribute("expensesGroups", expensesGroups.stream().filter(g -> g.getId() != -1).collect(Collectors.toList()));

        // Atributo usado para settear la clase 'active' en el item del menu que corresponda
        model.addAttribute("module", "expenses");
        return "abm/sharedExpenses";
    }

    @RequestMapping(value = "/sharedExpenses/create", method = RequestMethod.GET)
    public String createSharedExpense(Model model, @RequestParam("backUrl") Optional<String> backUrl) {
        SharedExpense sharedExpense = new SharedExpense();
        sharedExpense.setPayer(ApplicationUtils.getUserFromSession());
        sharedExpense.setCategory(categoryRepository.findById(Category.GENERIC_CATEGORY_ID).orElseThrow(() -> new ResourceNotFoundException("Category", "id", Category.GENERIC_CATEGORY_ID)));
        sharedExpense.setDate(new Date());
        AtomicLong sharedExpensesGroupId = new AtomicLong(-1L);
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
                        sharedExpensesGroupId.set(Long.parseLong(paramSharedExpensesGroupId));
                    }
                }
            }
        });
        expensesGroupRepository.findById(sharedExpensesGroupId.get()).ifPresent(expensesGroup -> {
            if (expensesGroup.getId() == -1L) {
                sharedExpense.setExpensesGroup(expensesGroup);
            } else {
                User user = ApplicationUtils.getUserFromSession();
                if (expensesGroup.getCreationUser().equals(user) || (expensesGroup.getMembers() != null && expensesGroup.getMembers().contains(user))) {
                    sharedExpense.setExpensesGroup(expensesGroup);
                }
            }
            model.addAttribute("expensesGroupMembers", getSharedExpensesGroupMembers(sharedExpensesGroupId.get()));
        });
        return getSharedExpensesEditPage(model, sharedExpense, backUrl);
    }

    @RequestMapping(value = "/sharedExpenses/edit", method = RequestMethod.GET)
    public String editSharedExpenses(Model model, @RequestParam(name = "sharedExpenseIdToEdit") Optional<Long> sharedExpenseIdToEdit, @RequestParam("backUrl") Optional<String> backUrl) {
        Optional<SharedExpense> sharedExpenseToEdit;
        if (sharedExpenseIdToEdit.isPresent()) {
            sharedExpenseToEdit = sharedExpenseRepository.findById(sharedExpenseIdToEdit.get());
            if (sharedExpenseToEdit.isPresent()) {
                SharedExpense sharedExpense = sharedExpenseToEdit.get();
                User user = ApplicationUtils.getUserFromSession();
                if (user.getAuthorities().stream().anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(Role.ADMIN.name()))
                        || sharedExpense.getPayer().equals(user)
                        || (sharedExpense.getExpensesGroup() != null && sharedExpense.getExpensesGroup().getMembers() != null && sharedExpense.getExpensesGroup().getMembers().contains(user))) {
                    return getSharedExpensesEditPage(model, sharedExpenseToEdit.get(), backUrl);
                } else {
                    model.addAttribute("applicationMessage", ApplicationMessage.warn("No tiene permitido editar este gasto"));
                }
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
                // TODO: Pensar si un usuario puede clonar gastos compartidos que no hayan sido creados por el. Por ahora no
                if (sharedExpenseToClone.get().getPayer().getId().equals(user.getId())) {
                    SharedExpense expense = ApplicationUtils.cloneEntity(sharedExpenseToClone.get(), true);
                    expense.setPayer(user);
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
    public String createOrUpdateSharedExpense(Model model, @Valid SharedExpense sharedExpense, BindingResult result, @RequestParam("backUrl") Optional<String> backUrl, @RequestParam("memberExpenses") Optional<String> sharedExpensesMembersJson) {
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

        List<SharedExpenseMember> sharedExpenseMembers = null;
        if (sharedExpensesMembersJson.isPresent()) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                List<MemberAmount> memberAmounts = objectMapper.readValue(sharedExpensesMembersJson.get(), new TypeReference<>() {});
                if (!CollectionUtils.isEmpty(memberAmounts)) {
                    sharedExpenseMembers = memberAmounts.stream().map(memberAmount -> {
                        SharedExpenseMember sharedExpenseMember = new SharedExpenseMember();
                        sharedExpenseMember.setUser(userRepository.findById(memberAmount.getMember()).orElseThrow(() -> new IllegalArgumentException("User not found")));
                        sharedExpenseMember.setAmount(memberAmount.getAmount());
                        return sharedExpenseMember;
                    }).collect(Collectors.toList());
                }
            } catch (JsonProcessingException e) {
                model.addAttribute("applicationMessage", ApplicationMessage.error("Error procesando datos de miembros"));
                return getSharedExpensesEditPage(model, sharedExpense, backUrl);
            }
        }

        if (!CollectionUtils.isEmpty(sharedExpenseMembers)) {
            List<SharedExpenseMember> currentMembers = sharedExpenseMemberRepository.findBySharedExpense(sharedExpense);
            List<SharedExpenseMember> newMembers = new ArrayList<>();
            // Mapa de los miembros actuales por usuario
            Map<User, SharedExpenseMember> currentMembersMap = currentMembers.stream()
                    .collect(Collectors.toMap(SharedExpenseMember::getUser, member -> member));

            // Iterar sobre los miembros actualizados
            for (SharedExpenseMember updatedMember : sharedExpenseMembers) {
                SharedExpenseMember currentMember = currentMembersMap.get(updatedMember.getUser());

                if (currentMember != null) {
                    // Actualizar el importe solo si cambió
                    if (currentMember.getAmount().compareTo(updatedMember.getAmount()) != 0) {
                        currentMember.setAmount(updatedMember.getAmount());
                        sharedExpenseMemberRepository.save(currentMember);
                    }
                    // El sharedExpense en este punto no tiene setteado los members porque vinieron en el parametro aparte. Tengo que setearlos nuevamente
                    newMembers.add(currentMember);

                    // Eliminar el miembro del mapa para identificar los que deben eliminarse
                    currentMembersMap.remove(updatedMember.getUser());
                } else {
                    // Agregar nuevo miembro
                    updatedMember.setSharedExpense(sharedExpense);
                    sharedExpenseMemberRepository.save(updatedMember);

                    // El sharedExpense en este punto no tiene setteado los members porque vinieron en el parametro aparte. Tengo que setearlos nuevamente
                    newMembers.add(updatedMember);
                }
            }
            sharedExpense.setMembers(newMembers);

            // Eliminar miembros que no están en sharedExpenseMembers
            sharedExpenseMemberRepository.deleteAll(currentMembersMap.values());
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

    private String getSharedExpensesEditPage(Model model, SharedExpense sharedExpense, Optional<String> backUrl) {
        model.addAttribute("sharedExpense", sharedExpense);
        String distributionMode = "sameAmount";
        List<MemberAmount> memberAmounts = new ArrayList<>();
        List<SharedExpenseMember> sharedExpenseMembers = sharedExpense.getMembers();
        if (!CollectionUtils.isEmpty(sharedExpenseMembers)) {
            Set<BigDecimal> amounts = new HashSet<>();
            for (SharedExpenseMember sharedExpenseMember : sharedExpenseMembers) {
                amounts.add(sharedExpenseMember.getAmount());
                memberAmounts.add(new MemberAmount(sharedExpenseMember.getUser().getId(), sharedExpenseMember.getAmount()));
            }
            if (amounts.size() > 1) distributionMode = "amount";
        }
        model.addAttribute("distributionMode", distributionMode);
        model.addAttribute("sharedExpenseMembers", memberAmounts);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("users", userRepository.findAllByEnabledTrueAndLockedFalse());
        // Atributo usado para settear la clase 'active' en el item del menu que corresponda
        model.addAttribute("module", "expenses");
        List<ExpensesGroup> expensesGroups = expensesGroupRepository.findAllByCreationUserOrMember(ApplicationUtils.getUserFromSession(), Sort.by(Sort.Direction.ASC, "name"));
        Optional<ExpensesGroup> sinGrupo = expensesGroupRepository.findById(-1L);
        sinGrupo.ifPresent(expensesGroups::add);
        model.addAttribute("expensesGroups", expensesGroups);

        List<Map<String, Object>> expensesGroupsList = new ArrayList<>();
        for (ExpensesGroup expensesGroup : expensesGroups) {
            Map<String, Object> expensesGroupMap = new HashMap<>();
            expensesGroupMap.put("id", expensesGroup.getId());
            expensesGroupMap.put("name", expensesGroup.getName());
            List<Map<String, Object>> expensesGroupMembersList = new ArrayList<>();
            List<User> expensesGroupMembers = expensesGroup.getId().equals(-1L) ? userRepository.findAllByEnabledTrueAndLockedFalse() : expensesGroup.getMembers();
            for (User member : expensesGroupMembers) {
                Map<String, Object> expensesGroupMembersMap = new HashMap<>();
                expensesGroupMembersMap.put("id", member.getId());
                expensesGroupMembersMap.put("fullName", member.getFullName());
                expensesGroupMembersList.add(expensesGroupMembersMap);
            }
            expensesGroupMap.put("members", expensesGroupMembersList);
            expensesGroupsList.add(expensesGroupMap);
        }
        model.addAttribute("expensesGroupsList", expensesGroupsList);

        backUrl.ifPresent(s -> model.addAttribute("backUrl", s));
        return "abm/sharedExpenses-edit";
    }

    @GetMapping("/sharedExpenses/groupMembers")
    public ResponseEntity<List<UserDTO>> getSharedExpensesGroupMembersAjax(@RequestParam(name = "groupId") Optional<Long> paramGroupId) {
        List<UserDTO> members = new ArrayList<>();
        if (paramGroupId.isPresent()) {
            long groupId = paramGroupId.get();
            members = getSharedExpensesGroupMembers(groupId).stream().map(user -> new UserDTO(user.getId(), user.getFullName())).collect(Collectors.toList());
        }

        return ResponseEntity.ok(members);
    }

    public List<User> getSharedExpensesGroupMembers(long groupId) {
        List<User> members = new ArrayList<>();
        if (groupId == -1) {
            members = userRepository.findAllByEnabledTrueAndLockedFalse();
        } else {
            Optional<ExpensesGroup> expensesGroup = expensesGroupRepository.findById(groupId);
            if (expensesGroup.isPresent()) {
                members = expensesGroup.get().getMembers();
            }
        }

        if (!members.isEmpty()) {
            members = members.stream().sorted(Comparator.comparing(User::getFullName)).collect(Collectors.toList());
        }

        return members;
    }
}

@Setter
@Getter
class UserDTO {
    private Long id;
    private String fullName;

    public UserDTO(Long id, String fullName) {
        this.id = id;
        this.fullName = fullName;
    }
}

@Setter
@Getter
class MemberAmount {
    private Long member;
    private BigDecimal amount;

    public MemberAmount() {
    }

    public MemberAmount(Long member, BigDecimal amount) {
        this.member = member;
        this.amount = amount;
    }
}