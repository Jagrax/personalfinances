package ignored.repository;

import ignored.entity.SharedExpense;
import ignored.entity.SharedExpenseMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SharedExpenseMemberRepository extends JpaRepository<SharedExpenseMember, Long>, JpaSpecificationExecutor<SharedExpenseMember> {

    List<SharedExpenseMember> findBySharedExpense(SharedExpense sharedExpense);
}