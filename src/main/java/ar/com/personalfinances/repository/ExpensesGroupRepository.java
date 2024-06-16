package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.ExpensesGroup;
import ar.com.personalfinances.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpensesGroupRepository extends JpaRepository<ExpensesGroup, Long>, JpaSpecificationExecutor<ExpensesGroup> {

    List<ExpensesGroup> findByCreationUser(User user);

    boolean existsByNameAndCreationUser(String name, User creationUSer);
}