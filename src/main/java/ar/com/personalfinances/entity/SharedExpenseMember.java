package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "shared_expense_members")
public class SharedExpenseMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_expense_id", nullable = false, foreignKey = @ForeignKey(name = "fk_shared_expense_member_shared_expense"))
    private SharedExpense sharedExpense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_shared_expense_member_user"))
    private User user;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Override
    public String toString() {
        return "SharedExpenseMember [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((sharedExpense != null) ? "sharedExpense=" + sharedExpense + ", " : "") +
                ((user != null) ? "user=" + user + ", " : "") +
                ((amount != null) ? "amount=" + amount + ", " : "") +
                "]";
    }
}