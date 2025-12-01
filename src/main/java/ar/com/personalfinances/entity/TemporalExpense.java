package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "temporal_expenses")
public class TemporalExpense extends Expense {

    @OneToOne
    @JoinColumn(name = "expense_id", foreignKey = @ForeignKey(name = "fk_tmp_expense_expense"))
    private Expense expense;

    @Override
    public String toString() {
        return "TemporalExpense [" +
                ((expense != null) ? "expense=" + expense + ", " : "") +
                "]";
    }
}