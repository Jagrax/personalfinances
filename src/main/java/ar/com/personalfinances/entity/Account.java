package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

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

    @Column(name = "sync_enabled", nullable = false)
    public boolean syncEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_provider")
    public SyncProvider syncProvider;

    @Column(name = "closing_day")
    private Integer closingDay;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Override
    public String toString() {
        return "Account [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((owner != null) ? "owner=" + owner + ", " : "") +
                ((name != null) ? "name='" + name + "', " : "") +
                ((type != null) ? "type=" + type + ", " : "") +
                ((subtype != null) ? "subtype='" + subtype + "', " : "") +
                ((currency != null) ? "currency='" + currency + "', " : "") +
                ((bank != null) ? "bank=" + bank + ", " : "") +
                "syncEnabled=" + syncEnabled + ", " +
                ((syncProvider != null) ? "syncProvider=" + syncProvider + ", " : "") +
                ((closingDay != null) ? "closingDate=" + closingDay + ", " : "") +
                ((lastSyncAt != null) ? "lastSyncAt=" + lastSyncAt + ", " : "") +
                "]";
    }
}