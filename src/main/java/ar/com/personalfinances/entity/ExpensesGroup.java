package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "expenses_groups", uniqueConstraints = {@UniqueConstraint(columnNames = {"name", "creation_user"})})
public class ExpensesGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "configurations")
    private String configurations;

    @ManyToOne
    @JoinColumn(name = "creation_user")
    private User creationUser;

    @OneToMany(mappedBy = "expensesGroup")
    private List<ExpensesGroupMember> members;

    @Override
    public String toString() {
        return "ExpensesGroup [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((name != null) ? "name='" + name + "', " : "") +
                ((description != null) ? "description='" + description + "', " : "") +
                ((configurations != null) ? "configurations='" + configurations + "', " : "") +
                ((creationUser != null) ? "creationUser=" + creationUser + ", " : "") +
                ((members != null) ? "members=" + Arrays.toString(members.toArray()) + ", " : "") +
                "]";
    }
}