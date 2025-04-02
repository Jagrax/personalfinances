package ar.com.personalfinances.controller.abm;

import ar.com.personalfinances.api.galicia.GaliciaApiManager;
import ar.com.personalfinances.api.galicia.GaliciaApiManagerBean;
import ar.com.personalfinances.api.galicia.model.CreditCardMovement;
import ar.com.personalfinances.api.galicia.model.Movimiento;
import ar.com.personalfinances.entity.*;
import ar.com.personalfinances.exception.ResourceNotFoundException;
import ar.com.personalfinances.repository.AccountRepository;
import ar.com.personalfinances.repository.CategoryRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.service.AlertEventService;
import ar.com.personalfinances.service.SpecificationsService;
import ar.com.personalfinances.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
public class BankSyncController {

    private final Category automaticCategory;

    private final SpecificationsService specificationsService;
    private final AccountRepository accountRepository;
    private final ExpenseRepository expenseRepository;
    private final AlertEventService alertEventService;

    public BankSyncController(SpecificationsService specificationsService, AccountRepository accountRepository, ExpenseRepository expenseRepository, AlertEventService alertEventService, CategoryRepository categoryRepository) {
        this.specificationsService = specificationsService;
        this.accountRepository = accountRepository;
        this.expenseRepository = expenseRepository;
        this.alertEventService = alertEventService;
        this.automaticCategory = categoryRepository.findById(Category.AUTOMATIC_CATEGORY_ID).orElseThrow(() -> new ResourceNotFoundException("Category", "id", Category.AUTOMATIC_CATEGORY_ID));
    }

    @RequestMapping(value = "/bank-sync", method = RequestMethod.GET)
    public String getBankSyncPage(Model model) {
        BankSyncModelAttribute bankSyncModelAttribute = new BankSyncModelAttribute();

        final Date to = new Date();
        bankSyncModelAttribute.setDateTo(to);

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(to);
        calendar.add(Calendar.DATE, -7);
        bankSyncModelAttribute.setDateFrom(calendar.getTime());

        model.addAttribute("bankSyncModelAttribute", bankSyncModelAttribute);

        List<Long> accountSearchOwnerIds = new ArrayList<>();
        accountSearchOwnerIds.add(-1L); // La cuenta Generica la pueden utilizar todos los usuarios

        User user = ApplicationUtils.getUserFromSession(false);
        if (user != null) {
            // Si no tengo al usuario, no puedo ver ninguna cuenta mas que la -1
            accountSearchOwnerIds.add(user.getId());
        }

        AccountSearch accountSearch = new AccountSearch();
        accountSearch.setOwnerIds(accountSearchOwnerIds);
        model.addAttribute("accounts", accountRepository.findAll(specificationsService.getAccounts(accountSearch), Sort.by(Sort.Direction.ASC,"name")));

        // Atributo usado para settear la clase 'active' en el item del menu que corresponda
        model.addAttribute("module", "expenses");

        return "abm/bank-sync";
    }

    @RequestMapping(value = "/bank-sync", method = RequestMethod.POST)
    public String postBankSync(@Valid BankSyncModelAttribute bankSyncModelAttribute, Model model) {

        if (bankSyncModelAttribute.getAccountId() == null) {
            return "redirect:/expenses";
        } else {
            Optional<Account> optionalAccount = accountRepository.findById(bankSyncModelAttribute.getAccountId());
            if (optionalAccount.isEmpty()) {
                return "redirect:/expenses";
            }
            final User user = ApplicationUtils.getUserFromSession();

            final Account account = optionalAccount.get();

            String applicationMessage = null;
            ApplicationMessage.ApplicationMessageType applicationMessageType = null;
            final GaliciaApiManager galiciaApiManager = new GaliciaApiManagerBean();
            switch (account.getType()) {
                case CREDIT_CARD: {
                    CommonResult getMovimientosTarjetaResult = galiciaApiManager.getMovimientosTarjeta(bankSyncModelAttribute.getCookie());
                    if (getMovimientosTarjetaResult.isError()) {
                        return "abm/bank-sync";
                    } else {
                        List<CreditCardMovement> movimientos = (List<CreditCardMovement>) getMovimientosTarjetaResult.getPayload();
                        if (!CollectionUtils.isEmpty(movimientos)) {
                            log.info("[postBankSync] Se procede a filtrar los movimientos ya sincronizados");
                            // 1) Filtro los movimientos del Galicia que ya existen en la DB
                            final long galiciaCurrencyARSId = 1;
                            final List<Long> expensesIdFounded = new ArrayList<>();
                            movimientos = movimientos.stream().filter(movimiento -> {
                                if (movimiento.getCurrency().equals(galiciaCurrencyARSId) && movimiento.getTotalInstallment().equals("0")) {
                                    // Una minima validacion: el movimiento tiene que tener todos los datos minimos requeridos
                                    if (isValid(movimiento)) {
                                        // Me fijo en los gastos existentes si alguno coincide con el que movimiento del Galicia
                                        final List<Expense> expensesByDateAndAmount = expenseRepository.findByDateAndAmountEquals(movimiento.getDate(), movimiento.getAmount());
                                        for (Expense expense : expensesByDateAndAmount) {
                                            if (expensesIdFounded.contains(expense.getId())) {
                                                continue;
                                            }

                                            expensesIdFounded.add(expense.getId());
                                            return false;
                                        }
                                    } else {
                                        // Si el gasto no es valido, lo descarto
                                        return false;
                                    }
                                } else {
                                    log.info("[postBankSync] Se ignora el {} por moneda invalida: {}", movimiento, movimiento.getCurrencySymbol());
                                    // Si el gasto no es en pesos (es en USD por ejemplo), hoy no me interesa guardarlo en la DB. Lo descarto
                                    return false;
                                }

                                // El gasto no existe en la DB y tiene los datos correctos. Lo guardo
                                return true;
                            }).collect(Collectors.toList());

                            log.info("[postBankSync] Luego de filtrar me quedaron {} movimientos", movimientos.size());

                            if (!CollectionUtils.isEmpty(movimientos)) {
                                movimientos.forEach(movimiento -> {
                                    String description = movimiento.getDescription();
                                    if (!StringUtils.hasText(description)) {
                                        description = movimiento.getMovementDescription();
                                    } else if (!description.equals(movimiento.getMovementDescription())) {
                                        description += " | " + movimiento.getMovementDescription();
                                    }
                                    createExpense(user, movimiento.getDate(), account, description, movimiento.getAmount());
                                });
                                applicationMessage = "Se sincronizaron " + movimientos.size() + " gastos en la cuenta";
                                applicationMessageType = ApplicationMessage.ApplicationMessageType.SUCCESS;
                            } else {
                                applicationMessage = "Los gastos de la cuenta estan sincronizados!";
                                applicationMessageType = ApplicationMessage.ApplicationMessageType.SUCCESS;
                            }
                        }
                    }
                    break;
                }
                case BANK_ACCOUNT: {
                    if (bankSyncModelAttribute.getDateFrom() != null && bankSyncModelAttribute.getDateTo() != null) {
                        CommonResult getMovimientosCuentaResult = galiciaApiManager.getMovimientosCuenta(bankSyncModelAttribute.getCookie(), bankSyncModelAttribute.getDateFrom(), bankSyncModelAttribute.getDateTo());
                        if (getMovimientosCuentaResult.isError()) {
                            return "abm/bank-sync";
                        }

                        List<Movimiento> movimientos = (List<Movimiento>) getMovimientosCuentaResult.getPayload();
                        if (!CollectionUtils.isEmpty(movimientos)) {
                            // TODO: Ir por el modelo de las tarjetas
                            ExpenseSearch expenseSearch = new ExpenseSearch();
                            expenseSearch.setAccountId(bankSyncModelAttribute.getAccountId());
                            Calendar calendar = Calendar.getInstance();
                            calendar.setTime(bankSyncModelAttribute.getDateFrom());
                            calendar.add(Calendar.DATE, -7);
                            expenseSearch.setDateFrom(calendar.getTime()); // Un GAP de 1 semana por si me vinieron movimientos con fechas posteriores a las reales (por fin de semana o feriados)
                            expenseSearch.setDateTo(bankSyncModelAttribute.getDateTo());
                            expenseSearch.setUserId(user.getId());
                            List<Expense> existingExpeses = expenseRepository.findAll(specificationsService.getExpenses(expenseSearch));
                            if (movimientos.size() != existingExpeses.size()) {
                                // Filtro los que ya existen
                                movimientos = movimientos.stream().filter(movimiento -> {
                                    for (Expense expense : existingExpeses) {
                                        boolean isSameDay = DateUtils.isSameDay(expense.getDate(), movimiento.getFecha());
                                        if (!isSameDay) {
                                            Calendar cal = Calendar.getInstance();
                                            cal.setTime(movimiento.getFecha());
                                            boolean esDiaHabil = false;
                                            while (!isSameDay && !esDiaHabil) {
                                                // Le resto 1 dia
                                                cal.add(Calendar.DATE, -1);
                                                // El dia anterior, fue habil?
                                                esDiaHabil = !(DateUtils.esFeriado(cal.getTime()) || DateUtils.esFinDeSemana(cal));
                                                // Si no lo fue, puede ser la fecha real (que esta cargada en la DB)
                                                if (!esDiaHabil) {
                                                    isSameDay = DateUtils.isSameDay(expense.getDate(), cal.getTime());
                                                }
                                            }
                                        }

                                        BigDecimal movimientoAmount =
                                                movimiento.getImporteCredito() != null && !NumberUtils.isZero(movimiento.getImporteCredito())
                                                        ? movimiento.getImporteCredito()
                                                        : movimiento.getImporteDebito() != null && !NumberUtils.isZero(movimiento.getImporteDebito())
                                                        ? movimiento.getImporteDebito()
                                                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                                        if (isSameDay && NumberUtils.bigDecimalIgual(expense.getAmount(), movimientoAmount)) {
                                            return false;
                                        }
                                    }

                                    return true;
                                }).collect(Collectors.toList());
                            }
                            // TODO: Obtener los movimientos de la cuenta y fijarse si hay alguno nuevo sin registrar
                            movimientos.forEach(movimiento -> {
                                final BigDecimal importeMovimiento =
                                        movimiento.getImporteCredito() != null && !NumberUtils.isZero(movimiento.getImporteCredito())
                                                ? movimiento.getImporteCredito()
                                                : movimiento.getImporteDebito() != null && !NumberUtils.isZero(movimiento.getImporteDebito())
                                                ? movimiento.getImporteDebito()
                                                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                                createExpense(user, movimiento.getFecha(), account, movimiento.getDescripcionAMostrar() + " | " + movimiento.getDescripcionSide(), importeMovimiento);
                            });
                        }
                    } else {
                        return "redirect:/expenses";
                    }
                    break;
                }
                default:
                    return "redirect:/expenses";
            }

            String redirectUrl = "/expenses?accountType=" + account.getType().name() + "&accountName=" + account.getName();
            if (applicationMessage != null) redirectUrl += "&applicationMessage=" + applicationMessage;
            if (applicationMessageType != null) redirectUrl += "&applicationMessageType=" + applicationMessageType.name();
            return "redirect:" + redirectUrl;
        }
    }

    private boolean isValid(CreditCardMovement creditCardMovement) {
        if (creditCardMovement.getDate() == null) {
            log.info("[isValid] Invalid {}: fecha is null", creditCardMovement);
            return false;
        } else if (creditCardMovement.getDescription() == null && creditCardMovement.getMovementDescription() == null) {
            log.info("[isValid] Invalid {}: description & movementDescription is null", creditCardMovement);
            return false;
        } else if (creditCardMovement.getAmount() == null) {
            log.info("[isValid] Invalid {}: amount is null", creditCardMovement);
            return false;
        }

        return true;
    }

    private Expense createExpense(User user, Date date, Account account, String description, BigDecimal amount) {
        Expense expense = new Expense();
        expense.setUser(user);
        expense.setDate(date);
        expense.setAccount(account);
        expense.setAmount(amount);
        expense.setDescription(description);
        expense.setCategory(automaticCategory);

        expense = expenseRepository.save(expense);
        log.info("[createExpense] Expense created: {}", expense.getId());
        alertEventService.saveExpenseAlert(EntityEvent.CREATED, expense.getId(), "", user.getId());
        return expense;
    }
}