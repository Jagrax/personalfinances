package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.SharedExpense;
import ar.com.personalfinances.entity.SharedExpenseMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SharedExpenseMemberRepository extends JpaRepository<SharedExpenseMember, Long>, JpaSpecificationExecutor<SharedExpenseMember> {

    List<SharedExpenseMember> findBySharedExpense(SharedExpense sharedExpense);
}