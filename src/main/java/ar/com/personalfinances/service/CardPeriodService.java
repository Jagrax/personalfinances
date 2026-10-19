package ar.com.personalfinances.service;

import ar.com.personalfinances.api.galicia.model.CardSettlements;
import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.CardPeriod;
import ar.com.personalfinances.entity.CardPeriodStatus;
import ar.com.personalfinances.entity.Expense;
import ar.com.personalfinances.repository.CardPeriodRepository;
import ar.com.personalfinances.repository.ExpenseRepository;
import ar.com.personalfinances.util.CommonResult;
import ar.com.personalfinances.util.DateUtils;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class CardPeriodService {

    private final GaliciaApiService galiciaApiService;
    private final CardPeriodRepository cardPeriodRepository;
    private final ExpenseRepository expenseRepository;

    public CardPeriodService(GaliciaApiService galiciaApiService, CardPeriodRepository cardPeriodRepository, ExpenseRepository expenseRepository) {
        this.galiciaApiService = galiciaApiService;
        this.cardPeriodRepository = cardPeriodRepository;
        this.expenseRepository = expenseRepository;
    }

    /**
     * Actualiza los periodos (card_periods) de la tarjeta con los cierres/vencimientos que homologa Galicia
     * (settlement_closing_dates y settlement_due_dates del overview) y mantiene sincronizado account.closingDay.
     * Si Galicia falla o no trae datos, no bloquea el sync de movimientos: solo quedan los periodos tal como estaban.
     */
    public void syncFromGalicia(Account creditCardAccount, String cookies) {
        final CommonResult settlementsResult = galiciaApiService.getCardSettlements(cookies);
        if (settlementsResult.isError()) {
            log.warn("[syncFromGalicia] No se pudo obtener los periodos de la tarjeta {}: {}", creditCardAccount.getName(), settlementsResult.getMessage());
            return;
        }

        final Map<String, CardSettlements> settlementsByAccountNumber = (Map<String, CardSettlements>) settlementsResult.getPayload();
        if (CollectionUtils.isEmpty(settlementsByAccountNumber)) {
            log.warn("[syncFromGalicia] Galicia no devolvio periodos para ninguna tarjeta");
            return;
        }

        final CardSettlements settlements = settlementsByAccountNumber.get(creditCardAccount.getExternalAccountId());
        if (settlements == null) {
            log.warn("[syncFromGalicia] Galicia no devolvio periodos para la tarjeta {} con externalAccountId {}", creditCardAccount.getName(), creditCardAccount.getExternalAccountId());
            return;
        }

        upsertPeriod(creditCardAccount, settlements.getPrevious(), null, settlements.getPreviousDue());
        upsertPeriod(creditCardAccount, settlements.getCurrent(), settlements.getPrevious() != null ? settlements.getPrevious().plusDays(1) : null, settlements.getCurrentDue());
        upsertPeriod(creditCardAccount, settlements.getNext(), settlements.getCurrent() != null ? settlements.getCurrent().plusDays(1) : null, settlements.getNextDue());

        // Cierro todo periodo cuya fecha de cierre ya paso (hoy inclusive)
        closeOverduePeriods(creditCardAccount);

        // Mantengo el fallback del dashboard (account.closingDay) sincronizado con el ultimo cierre cerrado
        updateClosingDay(creditCardAccount, settlements.getCurrent());
    }

    private void upsertPeriod(Account account, LocalDate closingDate, LocalDate periodStart, LocalDate dueDate) {
        if (closingDate == null) return;

        final Optional<CardPeriod> existingOpt = cardPeriodRepository.findByAccountAndClosingDate(account, closingDate);
        final CardPeriod period = existingOpt.orElseGet(() -> {
            CardPeriod newPeriod = new CardPeriod();
            newPeriod.setAccount(account);
            newPeriod.setClosingDate(closingDate);
            newPeriod.setStatus(CardPeriodStatus.OPEN);
            log.info("[syncFromGalicia] Creado periodo de tarjeta {}: cierre {}", account.getName(), DateUtils.format(closingDate));
            return newPeriod;
        });

        if (periodStart != null) period.setPeriodStart(periodStart);
        if (dueDate != null) period.setDueDate(dueDate);
        cardPeriodRepository.save(period);
    }

    private void closeOverduePeriods(Account account) {
        final List<CardPeriod> openPeriods = cardPeriodRepository.findByAccountAndStatusOrderByClosingDateDesc(account, CardPeriodStatus.OPEN);
        for (CardPeriod period : openPeriods) {
            if (!period.getClosingDate().isAfter(LocalDate.now())) {
                period.setStatus(CardPeriodStatus.CLOSED);
                cardPeriodRepository.save(period);
                log.info("[syncFromGalicia] Cerrado periodo de tarjeta {}: cierre {}", account.getName(), DateUtils.format(period.getClosingDate()));
            }
        }
    }

    private void updateClosingDay(Account creditCardAccount, LocalDate closingDate) {
        if (closingDate == null || closingDate.equals(creditCardAccount.getClosingDay())) return;

        log.info("[syncFromGalicia] Actualizada la fecha de cierre de la tarjeta {} de {} a {}", creditCardAccount.getName(), creditCardAccount.getClosingDay(), DateUtils.format(closingDate));
        creditCardAccount.setClosingDay(closingDate);
    }

    /**
     * Devuelve el periodo cuya franja [period_start .. closing_date] contiene la fecha dada.
     * Prefiere el periodo abierto. Devuelve null si no hay ningun periodo que la contenga.
     */
    public Optional<CardPeriod> findById(Long id) {
        return cardPeriodRepository.findById(id);
    }

    /**
     * Periodos de la tarjeta del mas viejo al mas reciente, que es como se muestran en el selector de la
     * columna "Periodo" de expenses.html.
     */
    public List<CardPeriod> findPeriodsInAscendingOrder(Account account) {
        return cardPeriodRepository.findByAccountOrderByClosingDateAsc(account);
    }

    public CardPeriod findPeriodForDate(Account account, LocalDate date) {
        if (date == null || account == null || account.getId() == null) return null;

        for (CardPeriod period : cardPeriodRepository.findByAccountAndStatusOrderByClosingDateDesc(account, CardPeriodStatus.OPEN)) {
            if (contains(period, date)) return period;
        }
        for (CardPeriod period : cardPeriodRepository.findByAccountAndStatusOrderByClosingDateDesc(account, CardPeriodStatus.CLOSED)) {
            if (contains(period, date)) return period;
        }
        return null;
    }

    /**
     * Devuelve el periodo abierto mas reciente de la tarjeta, o null si no hay ninguno.
     */
    public CardPeriod findOpenPeriod(Account account) {
        if (account == null || account.getId() == null) return null;
        final List<CardPeriod> openPeriods = cardPeriodRepository.findByAccountAndStatusOrderByClosingDateDesc(account, CardPeriodStatus.OPEN);
        return openPeriods.isEmpty() ? null : openPeriods.get(0);
    }

    /**
     * Devuelve el periodo inmediatamente posterior a {@code current} (el que arranca al dia siguiente de su cierre).
     * Devuelve null si no existe, sin caer en el periodo abierto.
     */
    public CardPeriod findNextPeriod(Account account, CardPeriod current) {
        if (account == null || account.getId() == null || current == null || current.getClosingDate() == null) return null;

        final LocalDate nextPeriodStart = current.getClosingDate().plusDays(1);
        return cardPeriodRepository.findByAccountOrderByClosingDateDesc(account).stream()
                .filter(period -> nextPeriodStart.equals(period.getPeriodStart()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Asigna el periodo correspondiente a todos los gastos de la tarjeta que todavia no tienen uno.
     * Idempotente: se puede correr en cada sync sin riesgo.
     */
    public void backfillPeriods(Account account) {
        if (account == null || account.getId() == null) return;

        final List<CardPeriod> periods = cardPeriodRepository.findByAccountOrderByClosingDateDesc(account);
        if (periods.isEmpty()) return;

        final List<Expense> expensesWithoutPeriod = expenseRepository.findByAccountAndPeriodIsNull(account);
        if (expensesWithoutPeriod.isEmpty()) return;

        int updated = 0;
        for (Expense expense : expensesWithoutPeriod) {
            final CardPeriod period = findPeriodForDate(account, expense.getDate());
            if (period == null) continue;
            expense.setPeriod(period);
            expenseRepository.save(expense);
            updated++;
        }
        if (updated > 0) {
            log.info("[backfillPeriods] Asignado periodo a {} gastos de la tarjeta {}", updated, account.getName());
        }
    }

    /**
     * Actualiza los periodos (card_periods) de la tarjeta con los que se deducen de los resumenes PDF y,
     * si no hay gastos en el PDF, con los que homologa Galicia (settlement_closing_dates y settlement_due_dates del overview).
     * Si Galicia falla o no trae datos, no bloquea el sync de movimientos: solo quedan los periodos tal como estaban.
     *
     * No hace backfill de periodos sobre los gastos: al importarse varios resumenes esto corre por PDF y
     * el backfill es un escaneo completo de la cuenta. El que importa debe correr una vez, con todos los
     * periodos ya dados de alta.
     */
    public void syncPeriodsFromSummaries(Account account, List<CardPeriod> periodsFromSummaries) {
        if (account == null || account.getId() == null || CollectionUtils.isEmpty(periodsFromSummaries)) return;

        for (CardPeriod period : periodsFromSummaries) {
            if (period == null || period.getClosingDate() == null) continue;
            upsertPeriod(account, period.getClosingDate(), period.getPeriodStart(), period.getDueDate());
        }

        // Cierro todo periodo cuya fecha de cierre ya paso (hoy inclusive)
        closeOverduePeriods(account);
    }

    /**
     * Asigna a cada gasto de la DB el periodo del resumen PDF con el que quedo emparejado.
     * Solo toca los gastos cuyo periodo difiere del que corresponde al resumen.
     */
    @Transactional
    public int syncPeriodsOfMatchedExpenses(Map<Expense, Expense> expensesFounded) {
        if (CollectionUtils.isEmpty(expensesFounded)) return 0;

        int updated = 0;
        for (Map.Entry<Expense, Expense> entry : expensesFounded.entrySet()) {
            final Expense dbExpense = entry.getKey();
            final CardPeriod periodFromPdf = entry.getValue().getPeriod();
            if (periodFromPdf == null) continue;
            if (dbExpense.getPeriod() != null && periodFromPdf.getId().equals(dbExpense.getPeriod().getId())) continue;

            dbExpense.setPeriod(periodFromPdf);
            expenseRepository.save(dbExpense);
            updated++;
        }

        if (updated > 0) {
            log.info("[syncPeriodsOfMatchedExpenses] Actualizado el periodo de {} gastos", updated);
        }
        return updated;
    }

    private boolean contains(CardPeriod period, LocalDate date) {
        if (period.getPeriodStart() == null) return false;
        return !date.isBefore(period.getPeriodStart()) && !date.isAfter(period.getClosingDate());
    }
}