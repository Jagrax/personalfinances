package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@Entity
@Table(name = "accounts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"owner_id", "type", "name"})
})
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", foreignKey = @ForeignKey(name = "fk_account_user"))
    @NotNull
    private User owner;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private AccountType type;

    @Column(name = "subtype")
    private String subtype;

    @Column(name = "currency")
    private String currency = "ARS";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id", foreignKey = @ForeignKey(name = "fk_account_bank"))
    private Bank bank;

    @Override
    public String toString() {
        return "Account [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((owner != null) ? "owner=" + owner + ", " : "") +
                ((name != null) ? "name='" + name + "', " : "") +
                ((type != null) ? "type=" + type + ", " : "") +
                ((subtype != null) ? "subtype='" + subtype + "', " : "") +
                ((currency != null) ? "currency='" + currency + "', " : "") +
                ((bank != null) ? "bank='" + bank + "', " : "") +
                "]";
    }
}