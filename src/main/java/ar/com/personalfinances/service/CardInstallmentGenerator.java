package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.CardPeriod;
import ar.com.personalfinances.entity.CardPeriodStatus;
import ar.com.personalfinances.entity.EntityEvent;
import ar.com.personalfinances.entity.Expense;
import ar.com.personalfinances.repository.CardPeriodRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CardInstallmentGenerator {

    private static final Pattern QUOTA_PATTERN = Pattern.compile("^Cuota (\\d+) de (\\d+)$");

    private final ExpenseRepository expenseRepository;
    private final CardPeriodRepository cardPeriodRepository;
    private final AlertEventService alertEventService;

    public CardInstallmentGenerator(ExpenseRepository expenseRepository, CardPeriodRepository cardPeriodRepository, AlertEventService alertEventService) {
        this.expenseRepository = expenseRepository;
        this.cardPeriodRepository = cardPeriodRepository;
        this.alertEventService = alertEventService;
    }

    /**
     * Materializa las cuotas siguientes de los periodos cerrados que aun no se procesaron.
     * Al pasar un periodo de OPEN a CLOSED, cada gasto "Cuota N de M" con N < M genera "Cuota N+1 de M"
     * en el periodo siguiente (el abierto). Idempotente gracias al flag installments_generated y al dedupe por detalle.
     */
    @Transactional
    public int generatePendingInstallments(Account account) {
        int generated = 0;
        if (account == null || account.getId() == null) return 0;

        final List<CardPeriod> periods = cardPeriodRepository.findByAccountOrderByClosingDateDesc(account);
        if (periods.isEmpty()) return 0;

        final List<CardPeriod> closedPeriodsToProcess = periods.stream()
                .filter(period -> CardPeriodStatus.CLOSED.equals(period.getStatus()) && !period.isInstallmentsGenerated())
                .sorted(Comparator.comparing(CardPeriod::getClosingDate))
                .collect(Collectors.toList());

        for (CardPeriod closedPeriod : closedPeriodsToProcess) {
            final CardPeriod nextPeriod = findNextPeriod(periods, closedPeriod);
            if (nextPeriod == null) {
                // Sin periodo siguiente no puedo colocar las cuotas: no marco el periodo para reintentar en el proximo sync
                log.warn("[generatePendingInstallments] No se encontro el periodo siguiente al cierre {} de la tarjeta {}. Se reintenta en el proximo sync", DateUtils.format(closedPeriod.getClosingDate()), account.getName());
                continue;
            }

            final List<Expense> periodInstallments = expenseRepository.findByAccountAndPeriod(account, closedPeriod);
            for (Expense installment : periodInstallments) {
                if (createNextInstallment(nextPeriod, installment)) generated++;
            }

            closedPeriod.setInstallmentsGenerated(true);
            cardPeriodRepository.save(closedPeriod);
        }

        if (generated > 0) {
            log.info("[generatePendingInstallments] Generadas {} cuotas siguientes para la tarjeta {}", generated, account.getName());
        }
        return generated;
    }

    private boolean createNextInstallment(CardPeriod nextPeriod, Expense installment) {
        if (installment.getAmount() == null) return false;

        final Matcher matcher = QUOTA_PATTERN.matcher(installment.getDetails() != null ? installment.getDetails().trim() : "");
        if (!matcher.matches()) return false;

        final int installmentNumber = Integer.parseInt(matcher.group(1));
        final int installmentPlan = Integer.parseInt(matcher.group(2));
        if (installmentNumber >= installmentPlan) return false;

        final String nextDetails = "Cuota " + (installmentNumber + 1) + " de " + installmentPlan;
        final boolean alreadyExists = !expenseRepository.findByAccountAndAmountEqualsAndDetailsLike(installment.getAccount(), installment.getAmount(), "%" + nextDetails + "%").isEmpty();
        if (alreadyExists) return false;

        Expense nextInstallment = new Expense();
        nextInstallment.setUser(installment.getUser());
        nextInstallment.setDate(installment.getDate());
        nextInstallment.setAccount(installment.getAccount());
        nextInstallment.setAmount(installment.getAmount());
        nextInstallment.setDescription(installment.getDescription());
        nextInstallment.setOriginalDescription(installment.getOriginalDescription());
        nextInstallment.setDetails(nextDetails);
        nextInstallment.setComments(installment.getComments());
        nextInstallment.setTags(new ArrayList<>(installment.getTags()));
        nextInstallment.setPeriod(nextPeriod);

        nextInstallment = expenseRepository.save(nextInstallment);
        log.info("[generatePendingInstallments] Creada {} de {} para {}", nextDetails, installment.getDescription(), DateUtils.format(nextInstallment.getDate()));
        alertEventService.saveExpenseAlert(EntityEvent.CREATED, nextInstallment.getId(), "", nextInstallment.getUser().getId());
        return true;
    }

    private CardPeriod findNextPeriod(List<CardPeriod> periods, CardPeriod closedPeriod) {
        final LocalDate nextPeriodStart = closedPeriod.getClosingDate().plusDays(1);
        return periods.stream()
                .filter(period -> nextPeriodStart.equals(period.getPeriodStart()))
                .findFirst()
                .orElseGet(() -> periods.stream()
                        .filter(period -> CardPeriodStatus.OPEN.equals(period.getStatus()))
                        .findFirst()
                        .orElse(null));
    }
}