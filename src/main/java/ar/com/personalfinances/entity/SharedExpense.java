package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "shared_expenses")
public class SharedExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "date", nullable = false)
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date date;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "details")
    private String details;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "comments")
    private String comments;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expenses_group_id")
    private ExpensesGroup expensesGroup;

    @OneToMany(mappedBy = "sharedExpense", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SharedExpenseMember> members;

    @Override
    public String toString() {
        return "SharedExpense [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((date != null) ? "date=" + date + ", " : "") +
                ((description != null) ? "description='" + description + "', " : "") +
                ((details != null) ? "details='" + details + "', " : "") +
                ((amount != null) ? "amount=" + amount + ", " : "") +
                ((comments != null) ? "comments='" + comments + "', " : "") +
                ((category != null) ? "category=" + category + ", " : "") +
                ((user != null) ? "user=" + user + ", " : "") +
                "]";
    }
}