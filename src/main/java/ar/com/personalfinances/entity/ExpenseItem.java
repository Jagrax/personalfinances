package ar.com.personalfinances.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "expense_item_tags",
            joinColumns = @JoinColumn(name = "expense_item_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @BatchSize(size = 25)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private List<Tag> tags = new ArrayList<>();

    @Override
    public String toString() {
        return "ExpenseItem [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((description != null) ? "description='" + description + "', " : "") +
                ((amount != null) ? "amount=" + amount + ", " : "") +
                ((tags != null) ? "tags=" + tags + ", " : "") +
                "]";
    }
}
