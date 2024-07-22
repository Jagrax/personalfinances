package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.validator.constraints.Length;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.persistence.*;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Setter
@Getter
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull(message = "First Name cannot be empty")
    @Column(name = "first_name")
    private String firstName;

    @NotNull(message = "Last Name cannot be empty")
    @Column(name = "last_name")
    private String lastName;

    @NotNull(message = "Email cannot be empty")
    @Email(message = "Please enter a valid email address")
    @Column(name = "email", unique = true)
    private String email;


    @NotNull(message = "Password cannot be empty")
    @Length(min = 7, message = "La contraseña debe tener un largo minimo de 7 caracteres")
    @Column(name = "password")
    private String password;

    @Column(name = "mobile")
    @Length(min = 10, message = "El numero de telefono debe tener al menos 10 digitos")
    private String mobile;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "locked")
    private Boolean locked = false;

    @Column(name = "enabled")
    private Boolean enabled = true;

    // Relation with other entities

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Account> accounts;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Category> categories;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Expense> expenses;

    @OneToMany(mappedBy = "creationUser", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpensesGroup> expensesGroups;

    @ManyToMany(mappedBy = "members", fetch = FetchType.LAZY)
    private List<SharedExpense> sharedExpenses;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SharedExpenseMember> expensesGroupsMembers;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role.name());
        return Collections.singletonList(authority);
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Override
    public String toString() {
        return "User [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((firstName != null) ? "firstName='" + firstName + "', " : "") +
                ((lastName != null) ? "lastName='" + lastName + "', " : "") +
                ((email != null) ? "email='" + email + "', " : "") +
                ((password != null) ? "password='" + password + "', " : "") +
                ((mobile != null) ? "mobile='" + mobile + "', " : "") +
                ((createdAt != null) ? "createdAt=" + createdAt + ", " : "") +
                ((updatedAt != null) ? "updatedAt=" + updatedAt + ", " : "") +
                ((role != null) ? "role=" + role + ", " : "") +
                ((locked != null) ? "locked=" + locked + ", " : "") +
                ((enabled != null) ? "enabled=" + enabled + ", " : "") +
                "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(firstName, user.firstName) && Objects.equals(lastName, user.lastName) && Objects.equals(email, user.email) && Objects.equals(password, user.password) && Objects.equals(mobile, user.mobile) && Objects.equals(createdAt, user.createdAt) && Objects.equals(updatedAt, user.updatedAt) && role == user.role && Objects.equals(locked, user.locked) && Objects.equals(enabled, user.enabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, firstName, lastName, email, password, mobile, createdAt, updatedAt, role, locked, enabled, sharedExpenses, expensesGroups);
    }
}