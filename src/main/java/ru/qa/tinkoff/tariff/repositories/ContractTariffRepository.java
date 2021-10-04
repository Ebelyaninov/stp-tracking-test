package ru.qa.tinkoff.tariff.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.qa.tinkoff.tariff.entities.ContractTariff;

import java.math.BigInteger;
import java.time.OffsetDateTime;

@Repository
public interface ContractTariffRepository extends JpaRepository<ContractTariff, BigInteger> {

    ContractTariff findContractTariffByContractIdAndEndDateGreaterThan (String contractId, OffsetDateTime endDate);
}
