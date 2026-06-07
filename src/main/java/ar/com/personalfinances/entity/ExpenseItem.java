package ar.com.personalfinances.entity;

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
    @JoinColumn(name = "expense_id", foreignKey = @ForeignKey(name = "fk_expense_item_expense"))
    private Expense expense;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "details")
    private String details;

    @Column(name = "comments")
    private String comments;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category1", foreignKey = @ForeignKey(name = "fk_expense_item1_category"))
    private Category category1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category2", foreignKey = @ForeignKey(name = "fk_expense_item2_category"))
    private Category category2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category3", foreignKey = @ForeignKey(name = "fk_expense_item3_category"))
    private Category category3;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category4", foreignKey = @ForeignKey(name = "fk_expense_item4_category"))
    private Category category4;

    @Override
    public String toString() {
        return "ExpenseItem [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((expense != null) ? "expense=" + expense + ", " : "") +
                ((description != null) ? "description='" + description + "', " : "") +
                ((amount != null) ? "amount=" + amount + ", " : "") +
                ((details != null) ? "details='" + details + "', " : "") +
                ((comments != null) ? "comments='" + comments + "', " : "") +
                ((category1 != null) ? "category1=" + category1 + ", " : "") +
                ((category2 != null) ? "category2=" + category2 + ", " : "") +
                ((category3 != null) ? "category3=" + category3 + ", " : "") +
                ((category4 != null) ? "category4=" + category4 + ", " : "") +
                "]";
    }
}