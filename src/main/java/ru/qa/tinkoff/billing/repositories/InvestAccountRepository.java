package ru.qa.tinkoff.billing.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.qa.tinkoff.billing.entities.InvestAccount;

import java.util.Optional;
import java.util.UUID;

public interface InvestAccountRepository  extends JpaRepository<InvestAccount, String> {

    Optional<InvestAccount> findById (UUID id);
}
