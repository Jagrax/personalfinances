package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportsRepository extends JpaRepository<User, Long> {

    @Query("SELECT a.name, a.currency, sum(e.amount), CASE WHEN a.type = 'BANK_ACCOUNT' THEN b.logo ELSE CASE WHEN LOWER(REPLACE(a.name, ' ', '')) = 'visa' THEN 'visa.svg' WHEN LOWER(REPLACE(a.name, ' ', '')) = 'mastercard' THEN 'mastercard.svg' WHEN LOWER(REPLACE(a.name, ' ', '')) = 'mercadopago' THEN 'mercadopago.svg' WHEN LOWER(REPLACE(a.name, ' ', '')) = 'sdd' THEN 'sdd.png' ELSE '' END END FROM Expense e, Account a, User u LEFT JOIN a.bank b WHERE u.id = :userId AND a.owner = u AND e.account = a GROUP BY a.id, a.currency")
    List<Object[]> getSumAmountsByAccount(@Param("userId") Long userId);

    @Query("SELECT date_format(e.date, '%m/%Y'), e.description, abs(e.amount) FROM Expense e, Category c WHERE e.category = c AND c.id = 8 AND e.date < '2020-08-01' ORDER BY e.date, e.description")
    List<Object[]> getReporteServicios();
}
