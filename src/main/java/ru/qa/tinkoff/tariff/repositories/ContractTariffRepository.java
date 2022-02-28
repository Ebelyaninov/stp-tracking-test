package ru.qa.tinkoff.tariff.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.qa.tinkoff.tariff.entities.ContractTariff;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ContractTariffRepository extends JpaRepository<ContractTariff, BigInteger> {

    List<ContractTariff> findContractTariffByContractIdOrderByEndDateDesc (String contractId);
}
