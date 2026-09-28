package ar.com.personalfinances.repository;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.CardPeriod;
import ar.com.personalfinances.entity.CardPeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CardPeriodRepository extends JpaRepository<CardPeriod, Long> {

    Optional<CardPeriod> findByAccountAndClosingDate(Account account, LocalDate closingDate);

    List<CardPeriod> findByAccountOrderByClosingDateDesc(Account account);

    List<CardPeriod> findByAccountAndStatusOrderByClosingDateDesc(Account account, CardPeriodStatus status);

    boolean existsByAccountIdAndStatus(Long accountId, CardPeriodStatus status);
}