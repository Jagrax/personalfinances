package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "categories")
public class Category {

    public final static long GENERIC_CATEGORY_ID = -1L;
    public final static long AUTOMATIC_CATEGORY_ID = -2L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_category_user"))
    private User owner;

    @Column(name = "color")
    private String color;

    @Override
    public String toString() {
        return "Category [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((name != null) ? "name='" + name + "', " : "") +
                ((owner != null) ? "user=" + owner + ", " : "") +
                ((color != null) ? "color='" + color + "', " : "") +
                "]";
    }
}