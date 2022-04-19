package ru.qa.tinkoff.tariff.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.tariff.entities.ContractTariff;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ContractTariffRepository extends JpaRepository<ContractTariff, BigInteger> {

    List<ContractTariff> findContractTariffByContractIdOrderByEndDateDesc (String contractId);

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "UPDATE tariff.contract_tariff " +
        "set tariff_id = :tariffId " +
        "WHERE id = :id")
    void updateContractTariffById (@Param(value = "tariffId") Integer tariffId, @Param("id") BigInteger id);
}
