package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.AccountApiCredentials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountApiCredentialsRepository extends JpaRepository<AccountApiCredentials, Long> {

    Optional<AccountApiCredentials> findByAccount(Account account);
}