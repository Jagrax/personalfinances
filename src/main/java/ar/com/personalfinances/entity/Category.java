package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "categories")
public class Category {

    public final static long GENERIC_CATEGORY_ID = -1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_category_user"))
    private User owner;

    @Override
    public String toString() {
        return "Category [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((name != null) ? "name='" + name + "', " : "") +
                ((owner != null) ? "user=" + owner + ", " : "") +
                "]";
    }
}