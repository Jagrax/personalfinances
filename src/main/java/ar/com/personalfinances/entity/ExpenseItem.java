package ar.com.personalfinances.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "expense_items")
public class ExpenseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false, foreignKey = @ForeignKey(name = "fk_expense_item_expense"))
    @JsonIgnore
    private Expense expense;

    public Long getExpenseId() {
        return expense != null ? expense.getId() : null;
    }

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false, foreignKey = @ForeignKey(name = "fk_expense_item_category"))
    @JsonIgnoreProperties({"owner", "hibernateLazyInitializer", "handler"})
    private Category category;

    @Override
    public String toString() {
        return "ExpenseItem [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((description != null) ? "description='" + description + "', " : "") +
                ((amount != null) ? "amount=" + amount + ", " : "") +
                ((category != null) ? "category=" + category + ", " : "") +
                "]";
    }
}
