package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "shared_expense_participants")
public class SharedExpenseParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_expense_id")
    private SharedExpense sharedExpense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Override
    public String toString() {
        return "SharedExpenseParticipant [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((sharedExpense != null) ? "sharedExpense=" + sharedExpense + ", " : "") +
                ((user != null) ? "user=" + user + ", " : "") +
                ((amount != null) ? "amount=" + amount + ", " : "") +
                "]";
    }
}