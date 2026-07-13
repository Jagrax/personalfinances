package ar.com.personalfinances.entity;

import ar.com.personalfinances.util.DateUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "date", nullable = false)
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "original_description")
    private String originalDescription;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "details")
    private String details;

    @Column(name = "comments")
    private String comments;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", foreignKey = @ForeignKey(name = "fk_expense_category"))
    private Category category = new Category();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", foreignKey = @ForeignKey(name = "fk_expense_account"))
    private Account account = new Account();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_expense_user"))
    @JsonIgnoreProperties({"expenses", "hibernateLazyInitializer", "handler"})
    private User user;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @BatchSize(size = 25)
    private List<ExpenseItem> items = new ArrayList<>();

    public String toDebugString() {
        return "Expense [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((date != null) ? "date=" + DateUtils.format(date) + ", " : "") +
                ((description != null) ? "description='" + description + "', " : "") +
                ((originalDescription != null) ? "originalDescription='" + originalDescription + "', " : "") +
                ((amount != null) ? "amount=" + amount : "") +
                "]";
    }

    @Override
    public String toString() {
        return "Expense [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((date != null) ? "date=" + date + ", " : "") +
                ((description != null) ? "description='" + description + "', " : "") +
                ((originalDescription != null) ? "originalDescription='" + originalDescription + "', " : "") +
                ((amount != null) ? "amount=" + amount + ", " : "") +
                ((details != null) ? "details='" + details + "', " : "") +
                ((comments != null) ? "comments='" + comments + "', " : "") +
                ((category != null) ? "category=" + category + ", " : "") +
                ((account != null) ? "account=" + account + ", " : "") +
                ((user != null) ? "user=" + user + ", " : "") +
                "]";
    }
}
