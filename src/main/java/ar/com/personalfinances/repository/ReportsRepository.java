package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportsRepository extends JpaRepository<User, Long> {

    @Query("SELECT a.name, " +
            "SUM(CASE WHEN e.sourceAccount = a OR e.targetAccount = a THEN e.amount ELSE 0 END) " +
            "FROM User u " +
            "JOIN Account a ON a.owner = u " +
            "JOIN Expense e ON e.user = u " +
            "WHERE u.id = :userId " +
            "GROUP BY a.id, a.name, u.id " +
            "ORDER BY a.name")
    List<Object[]> obtenerDatosEspecificosPorUsuario(@Param("userId") Long userId);
}
