package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

@Getter
@Setter
@Entity
@Table(name = "card_periods", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"account_id", "closing_date"})
})
public class CardPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", foreignKey = @ForeignKey(name = "fk_card_period_account"))
    @NotNull
    private Account account;

    @Column(name = "closing_date", nullable = false)
    private LocalDate closingDate;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CardPeriodStatus status = CardPeriodStatus.OPEN;

    @Column(name = "installments_generated", nullable = false)
    private boolean installmentsGenerated = false;

    private static final String[] SPANISH_MONTHS = {
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    @Transient
    public String getPeriodMonthLabel() {
        YearMonth month = resolvePeriodMonth();
        if (month == null) return null;
        return SPANISH_MONTHS[month.getMonthValue() - 1] + " " + month.getYear();
    }

    private YearMonth resolvePeriodMonth() {
        if (periodStart == null) {
            return closingDate != null ? YearMonth.from(closingDate) : null;
        }
        if (closingDate == null) return null;
        YearMonth best = YearMonth.from(periodStart);
        long bestDays = -1;
        YearMonth cursor = YearMonth.from(periodStart);
        YearMonth end = YearMonth.from(closingDate);
        while (!cursor.isAfter(end)) {
            LocalDate monthStart = cursor.atDay(1);
            LocalDate monthEnd = cursor.atEndOfMonth();
            LocalDate overlapStart = periodStart.isAfter(monthStart) ? periodStart : monthStart;
            LocalDate overlapEnd = closingDate.isBefore(monthEnd) ? closingDate : monthEnd;
            long days = ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
            if (days > bestDays) {
                bestDays = days;
                best = cursor;
            }
            cursor = cursor.plusMonths(1);
        }
        return best;
    }

    @Override
    public String toString() {
        return "CardPeriod [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((account != null) ? "account=" + account + ", " : "") +
                ((closingDate != null) ? "closingDate=" + closingDate + ", " : "") +
                ((periodStart != null) ? "periodStart=" + periodStart + ", " : "") +
                ((dueDate != null) ? "dueDate=" + dueDate + ", " : "") +
                status +
                "]";
    }
}