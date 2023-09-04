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
           "       sum(e.amount) " +
           "FROM Expense e," +
           "     Account a," +
           "     User u " +
           "WHERE u.id = :userId" +
           "  AND a.owner = u" +
           "  AND e.account = a " +
           "GROUP BY a.id")
    List<Object[]> obtenerDatosEspecificosPorUsuario(@Param("userId") Long userId);
}
