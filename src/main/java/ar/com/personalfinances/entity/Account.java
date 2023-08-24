package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@Entity
@Table(name = "accounts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"name", "type"})
})
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    @NotNull
    private User owner;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private AccountType type;

    @Override
    public String toString() {
        return "Account [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((owner != null) ? "owner=" + owner + ", " : "") +
                ((name != null) ? "name='" + name + "', " : "") +
                ((type != null) ? "type=" + type + ", " : "") +
                "]";
    }
}