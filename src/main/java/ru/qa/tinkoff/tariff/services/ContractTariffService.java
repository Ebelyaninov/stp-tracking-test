package ru.qa.tinkoff.tariff.services;


import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.tariff.entities.ContractTariff;
import ru.qa.tinkoff.tariff.repositories.ContractTariffRepository;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractTariffService {
    private final ContractTariffRepository contractTariffRepository;

    @Step("Обновить id тарифа в БД тарифного модуля")
    @SneakyThrows
    public void  updateTariffIdByContract (Integer tariffId, String contractId, OffsetDateTime endDate) {
        ContractTariff contract = contractTariffRepository.findContractTariffByContractIdOrderByEndDateDesc (contractId).stream()
                                        .findFirst().get();
        log.info("Successfully find  contract  {}", contract);
        contract.setTariffId(tariffId);
        contractTariffRepository.save(contract);
        log.info("Contract was updated {}", contract);
    }
}
