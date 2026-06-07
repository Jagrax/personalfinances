package ar.com.personalfinances.entity;

import ar.com.personalfinances.util.CryptoConverter;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "account_api_credentials",
        uniqueConstraints = @UniqueConstraint(columnNames = {"account_id"}))
public class AccountApiCredentials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_credentials_account"))
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private SyncProvider provider;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "username_enc", nullable = false)
    private String usernameEncrypted;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "password_enc", nullable = false)
    private String passwordEncrypted;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "extra_data_enc")
    private String extraDataEncrypted;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Override
    public String toString() {
        return "AccountApiCredentials [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((account != null) ? "account=" + account + ", " : "") +
                ((provider != null) ? "provider=" + provider + ", " : "") +
                ((usernameEncrypted != null) ? "usernameEncrypted='" + usernameEncrypted + "', " : "") +
                ((passwordEncrypted != null) ? "passwordEncrypted='" + passwordEncrypted + "', " : "") +
                ((extraDataEncrypted != null) ? "extraDataEncrypted='" + extraDataEncrypted + "', " : "") +
                ((createdAt != null) ? "createdAt=" + createdAt + ", " : "") +
                "]";
    }
}