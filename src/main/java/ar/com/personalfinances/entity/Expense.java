package ar.com.personalfinances.entity;

import javax.persistence.*;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.Date;

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
    private Date date;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "details")
    private String details;

    @Column(name = "comments")
    private String comments;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_account_id")
    private Account targetAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Override
    public String toString() {
        return "Expense [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((date != null) ? "date=" + date + ", " : "") +
                ((description != null) ? "description='" + description + "', " : "") +
                ((amount != null) ? "amount=" + amount + ", " : "") +
                ((details != null) ? "details='" + details + "', " : "") +
                ((comments != null) ? "comments='" + comments + "', " : "") +
                ((category != null) ? "category=" + category + ", " : "") +
                ((sourceAccount != null) ? "sourceAccount=" + sourceAccount + ", " : "") +
                ((targetAccount != null) ? "targetAccount=" + targetAccount + ", " : "") +
                ((user != null) ? "user=" + user + ", " : "") +
                "]";
    }
}