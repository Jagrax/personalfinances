package ar.com.personalfinances.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "banks")
public class Bank {

    public final static long GENERIC_BANK_ID = -1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "logo")
    private String logo;

    @Override
    public String toString() {
        return "Bank [" +
                ((id != null) ? "id=" + id + ", " : "") +
                ((name != null) ? "name='" + name + "', " : "") +
                ((logo != null) ? "logo='" + logo + "', " : "") +
                "]";
    }
}