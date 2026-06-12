package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportsRepository extends JpaRepository<User, Long> {

    @Query(value = "SELECT a.id, a.name, a.currency, COALESCE(SUM(e.amount), 0), " +
            "COALESCE(a.icon, b.logo) AS icon, a.sync_enabled, a.type, a.sync_provider, " +
            "b.id AS bank_id, b.name AS bank_name " +
            "FROM accounts a " +
            "LEFT JOIN banks b ON b.id = a.bank_id " +
            "LEFT JOIN expenses e ON e.account_id = a.id " +
            "WHERE a.owner_id = :userId " +
            "GROUP BY a.id, a.name, a.currency, a.type, a.sync_enabled, a.sync_provider, a.icon, b.logo, b.id, b.name " +
            "ORDER BY a.type, a.name", nativeQuery = true)
    List<Object[]> getSumAmountsByAccount(@Param("userId") Long userId);

    @Query(value = "SELECT date_format(e.date, '%m/%Y'), e.description, abs(e.amount) FROM expenses e, categories c WHERE e.category_id = c.id AND c.id = 8 AND e.date < '2020-08-01' ORDER BY e.date, e.description", nativeQuery = true)
    List<Object[]> getReporteServicios();
}
