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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
public class BankSyncController {

    private final SpecificationsService specificationsService;
    private final AccountRepository accountRepository;
    private final ExpenseRepository expenseRepository;
    private final AlertEventService alertEventService;
    private final CategoryRepository categoryRepository;

    public BankSyncController(SpecificationsService specificationsService, AccountRepository accountRepository, ExpenseRepository expenseRepository, AlertEventService alertEventService, CategoryRepository categoryRepository) {
        this.specificationsService = specificationsService;
        this.accountRepository = accountRepository;
        this.expenseRepository = expenseRepository;
        this.alertEventService = alertEventService;
        this.categoryRepository = categoryRepository;
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
            User user = ApplicationUtils.getUserFromSession();

            Account account = optionalAccount.get();
            GaliciaApiManager galiciaApiManager = new GaliciaApiManagerBean();
            if (!bankSyncModelAttribute.isCreditCard() && bankSyncModelAttribute.getDateFrom() != null && bankSyncModelAttribute.getDateTo() != null) {
                CommonResult getMovimientosCuentaResult = galiciaApiManager.getMovimientosCuenta(bankSyncModelAttribute.getCookie(), bankSyncModelAttribute.getDateFrom(), bankSyncModelAttribute.getDateTo());
                if (getMovimientosCuentaResult.isError()) {

                }

                List<Movimiento> movimientos = (List<Movimiento>) getMovimientosCuentaResult.getPayload();
                if (!CollectionUtils.isEmpty(movimientos)) {
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
                    final Category genericCategory = categoryRepository.findById(Category.GENERIC_CATEGORY_ID).orElseThrow(() -> new ResourceNotFoundException("Category", "id", Category.GENERIC_CATEGORY_ID));
                    final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                    movimientos.forEach(movimiento -> {
                        final BigDecimal importeMovimiento =
                                movimiento.getImporteCredito() != null && !NumberUtils.isZero(movimiento.getImporteCredito())
                                        ? movimiento.getImporteCredito()
                                        : movimiento.getImporteDebito() != null && !NumberUtils.isZero(movimiento.getImporteDebito())
                                        ? movimiento.getImporteDebito()
                                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                        Expense expense = new Expense();
                        expense.setUser(user);
                        expense.setDate(movimiento.getFecha());
                        expense.setAccount(account);
                        expense.setAmount(importeMovimiento);
                        expense.setDescription(movimiento.getDescripcionAMostrar() + " | " + movimiento.getDescripcionSide());
                        expense.setCategory(genericCategory);

                        expense = expenseRepository.save(expense);
                        alertEventService.saveExpenseAlert(EntityEvent.CREATED, expense.getId(), "", user.getId());

                        String detalleMovimiento = "";
                        final Date fechaMovimiento = movimiento.getFecha();
                        if (fechaMovimiento != null) {
                            detalleMovimiento += sdf.format(fechaMovimiento);
                        }
                        final String descriptionMovimiento = movimiento.getDescripcionSide();
                        if (StringUtils.hasText(descriptionMovimiento)) {
                            detalleMovimiento += " " + descriptionMovimiento;
                        }

                        detalleMovimiento += " " + importeMovimiento + " ARS";
                        log.info(detalleMovimiento);
                    });
                }
            } else {
                CommonResult getMovimientosTarjetaResult = galiciaApiManager.getMovimientosTarjeta(bankSyncModelAttribute.getCookie());
                if (getMovimientosTarjetaResult.isError()) {

                }

                List<CreditCardMovement> movimientos = (List<CreditCardMovement>) getMovimientosTarjetaResult.getPayload();
                if (!CollectionUtils.isEmpty(movimientos)) {
                    // TODO: Obtener los movimientos de la cuenta y fijarse si hay alguno nuevo sin registrar
                    ExpenseSearch expenseSearch = new ExpenseSearch();
                    expenseSearch.setAccountId(bankSyncModelAttribute.getAccountId());
                    expenseSearch.setDateFrom(bankSyncModelAttribute.getDateFrom());
                    expenseSearch.setDateTo(bankSyncModelAttribute.getDateTo());
                    expenseSearch.setUserId(user.getId());
                    List<Expense> existingExpeses = expenseRepository.findAll(specificationsService.getExpenses(expenseSearch));
                    if (movimientos.size() != existingExpeses.size()) {
                        // Filtro los que ya existen
                        movimientos = movimientos.stream().filter(movimiento -> {
                            if (movimiento.getCurrency().equals(1L)) {
                                for (Expense expense : existingExpeses) {
                                    boolean isSameDay = DateUtils.isSameDay(expense.getDate(), movimiento.getDate());
                                    Calendar calendar = Calendar.getInstance();
                                    calendar.setTime(movimiento.getDate());
                                    boolean esDiaHabil = false;
                                    while (!isSameDay && !esDiaHabil) {
                                        calendar.add(Calendar.DATE, -1);
                                        esDiaHabil = !(DateUtils.esFeriado(calendar.getTime()) || DateUtils.esFinDeSemana(calendar));
                                        if (!esDiaHabil) {
                                            isSameDay = DateUtils.isSameDay(expense.getDate(), calendar.getTime());
                                        }
                                    }
                                    BigDecimal movimientoAmount = movimiento.getAmount() != null ? movimiento.getAmount() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                                    if (isSameDay && NumberUtils.bigDecimalIgual(expense.getAmount(), movimientoAmount)) {
                                        return false;
                                    }
                                }
                            } else {
                                return false;
                            }


                            return true;
                        }).collect(Collectors.toList());
                    }

                    if (!CollectionUtils.isEmpty(movimientos)) {
                        final Category genericCategory = categoryRepository.findById(Category.GENERIC_CATEGORY_ID).orElseThrow(() -> new ResourceNotFoundException("Category", "id", Category.GENERIC_CATEGORY_ID));
                        final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                        movimientos.forEach(movimiento -> {

                            Expense expense = new Expense();
                            expense.setUser(user);
                            expense.setDate(movimiento.getDate());
                            expense.setAccount(account);
                            expense.setAmount(movimiento.getAmount());
                            expense.setDescription(movimiento.getDescription() + " | " + movimiento.getMovementDescription());
                            expense.setCategory(genericCategory);

                            expense = expenseRepository.save(expense);
                            alertEventService.saveExpenseAlert(EntityEvent.CREATED, expense.getId(), "", user.getId());


                            String detalleMovimiento = "";
                            final Date fechaMovimiento = movimiento.getDate();
                            if (fechaMovimiento != null) {
                                detalleMovimiento += sdf.format(fechaMovimiento);
                            }
                            final String descriptionMovimiento = movimiento.getDescription();
                            if (StringUtils.hasText(descriptionMovimiento)) {
                                detalleMovimiento += " " + descriptionMovimiento;
                            }
                            final BigDecimal importeMovimiento = movimiento.getAmount() != null ? movimiento.getAmount() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                            detalleMovimiento += " " + importeMovimiento + " " + movimiento.getCurrencySymbol();
                            log.info(detalleMovimiento);
                        });
                    }
                }
            }
            return "redirect:/expenses?accountType=" + account.getType().name() + "&accountName=" + account.getName();
        }
    }
}
