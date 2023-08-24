package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.AccountType;
import ar.com.personalfinances.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    List<Account> findByOwner(User owner);

    List<Account> findByOwnerAndType(User owner, AccountType accountType);
}