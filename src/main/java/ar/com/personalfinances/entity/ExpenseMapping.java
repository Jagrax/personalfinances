package ar.com.personalfinances.entity;

import ar.com.personalfinances.util.DateUtils;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Getter
@Setter
@Entity
@Table(name = "expense_mappings")
public class ExpenseMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "creation_date", nullable = false)
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate creationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_expense_mapping_user"))
    private User user;

    @Column(name = "bank_description")
    private String bankDescription;

    @Column(name = "regex_pattern")
    private String regexPattern;

    @Column(name = "normalized_description")
    private String normalizedDescription;

    @Column(name = "details")
    private String details;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "expense_mapping_tags",
            joinColumns = @JoinColumn(name = "expense_mapping_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<Tag> tags = new ArrayList<>();

    @Column(name = "enabled")
    private Boolean enabled = true;

    public void validateRegex(String pattern) {
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Regex inválido: " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "ExpenseMapping [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((creationDate != null) ? "creationDate=" + DateUtils.format(creationDate) + ", " : "") +
                ((user != null) ? "user=" + user + ", " : "") +
                ((bankDescription != null) ? "bankDescription='" + bankDescription + "', " : "") +
                ((regexPattern != null) ? "regexPattern='" + regexPattern + "', " : "") +
                ((normalizedDescription != null) ? "normalizedDescription='" + normalizedDescription + "', " : "") +
                ((details != null) ? "details='" + details + "', " : "") +
                ((tags != null) ? "tags=" + tags + ", " : "") +
                ((enabled != null) ? "enabled=" + enabled + ", " : "") +
                "]";
    }
}