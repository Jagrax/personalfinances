package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportsRepository extends JpaRepository<User, Long> {

    @Query(value = "SELECT date_format(e.date, '%m/%Y'), e.description, abs(e.amount) FROM expenses e LEFT JOIN expense_tags et ON et.expense_id = e.id LEFT JOIN tags t ON t.id = et.tag_id WHERE t.name = 'Servicios' AND e.date < '2020-08-01' ORDER BY e.date, e.description", nativeQuery = true)
    List<Object[]> getReporteServicios();
}
