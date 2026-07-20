package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "tags")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "color")
    private String color;

    @Override
    public String toString() {
        return "Tag [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((name != null) ? "name='" + name + "', " : "") +
                ((color != null) ? "color='" + color + "', " : "") +
                "]";
    }
}