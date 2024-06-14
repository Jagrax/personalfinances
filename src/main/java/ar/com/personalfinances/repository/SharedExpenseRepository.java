package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.SharedExpense;
import ar.com.personalfinances.entity.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SharedExpenseRepository extends JpaRepository<SharedExpense, Long>, JpaSpecificationExecutor<SharedExpense> {

    List<SharedExpense> findByUser(User user, Sort sort);
}