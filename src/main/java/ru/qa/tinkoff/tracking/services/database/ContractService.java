package ru.qa.tinkoff.tracking.services.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractRole;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.repositories.ContractRepository;
import ru.qa.tinkoff.tracking.repositories.StrategyRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

//************************методы для работы с таблицей contract БД postgres@db-tracking.trading.local


@Slf4j
@Service
@AllArgsConstructor
public class ContractService {

    final ContractRepository contractRepository;
    final StrategyRepository strategyRepository;
    final ObjectMapper objectMapper;

    @Step("Поиск контракта по id")
    @SneakyThrows
    public Contract getContract(String contractId) {
        Optional<Contract> contract = contractRepository.findById(contractId);
        log.info("Successfully find contract {}", contractId);
        Allure.addAttachment("Найденный контракт", "application/json", objectMapper.writeValueAsString(contractId));
        return contract.orElseThrow(() -> new RuntimeException("Не найден контракт"));
    }

    @Step("Поиск контракта по id")
    @SneakyThrows
    public Optional<Contract> findContract(String contractId) {
        Optional<Contract> contract = contractRepository.findById(contractId);
        log.info("Successfully find contract {}", contractId);
        Allure.addAttachment("Найденный контракт", "application/json", objectMapper.writeValueAsString(contractId));
        return contract;
    }


    @Step("Удаление контракта")
    @SneakyThrows
    public void deleteContract(Contract contract) {
        Allure.addAttachment("Удаленный контракт", "application/json", objectMapper.writeValueAsString(contract));
        contractRepository.delete(contract);
        log.info("Successfully deleted strategy {}", contract.toString());
    }


    @Step("Удаление контракта по id")
    @SneakyThrows
    public Contract deleteContractById(String contractId) {
        Contract contract = contractRepository.deleteContractById(contractId);
        log.info("Successfully find contract {}", contractId);
        Allure.addAttachment("Удаленый контракт", "application/json", objectMapper.writeValueAsString(contractId));
        return contract;
    }


    @SneakyThrows
    @Step("Сохранение контракта")
    public Contract saveContract(Contract contract)  {
        Contract saved = contractRepository.save(contract);
        log.info("Successfully saved contract {}", saved);
        Allure.addAttachment("Контракт", "application/json", objectMapper.writeValueAsString(saved));
        return saved;
    }


    @Step("Изменение роли, статуса и Id стратегии контракта")
    public Contract updateBlockedContract(String id, boolean blocked) throws JsonProcessingException {
        Contract contract = contractRepository.findById(id).orElseThrow(() -> new RuntimeException("Contract not found"));
//        contract.setRole(role);
//        contract.setState(state);
//        contract.setStrategyId(strategyId);
        contract.setBlocked(blocked);
        contractRepository.save(contract);
        log.info("Successfully update contract {}", contract);
        Allure.addAttachment("Контракт", "application/json", objectMapper.writeValueAsString(contract));
        return contract;
    }


    @Step("Изменение роли контракта")
    public Contract updateRoleContract(String id, ContractRole role) throws JsonProcessingException {
        Contract contract = contractRepository.findById(id).orElseThrow(() -> new RuntimeException("Contract not found"));
//        contract.setRole(role);
        contractRepository.save(contract);
        log.info("Successfully update contract {}", contract);
        Allure.addAttachment("Контракт", "application/json", objectMapper.writeValueAsString(contract));
        return contract;
    }

    @Step("Поиск контакта")
    @SneakyThrows
    public Optional<Contract> findOneContract() {
        Optional<Contract> contract = Optional.ofNullable(contractRepository.selectOneContract());
        log.info("Successfully find exchangePosition {}", contract);
        Allure.addAttachment("Найденный контакт", "application/json", objectMapper.writeValueAsString(contract));
        return contract;
    }


    @Step("Удаление контракта по идентификатору")
    @SneakyThrows
    public void deleteStrategyByIds(Collection<String> ids) {
        if (ids.isEmpty()) {
            log.error("Удаление стратегий не выполняется - пустой список идентификаторов стратегий");
        }
        // todo Allure.addAttachment("Удаленная стратегия", "application/json", strategy));
        contractRepository.deleteContractsByIdIn(ids);
        log.info("Successfully deleted strategy {}", ids);
    }


    @Step("Поиск стратегий по статусу и не пустому значению Profile")
    @SneakyThrows
    public List<Strategy> getStrategyByStatusWithProfile(StrategyStatus status) {
        List<Strategy> strategyByStatusNative = strategyRepository.findStrategyByStatusNative(status.name());
        log.info("Successfully find strategy {}", status);
        Allure.addAttachment("Найденная стратегия по статусу", "application/json", objectMapper.writeValueAsString(status));
       return strategyByStatusNative;
    }

    @Step("Поиск стратегий по статусам и не пустому значению Profile")
    @SneakyThrows
    public List<Strategy> getStrategyByTwoStatusWithProfile(StrategyStatus firstStatus, StrategyStatus secondStatus) {
        List<Strategy> strategyByStatusNative = strategyRepository.findStrategyByTwoStatusesNative(firstStatus.name(), secondStatus.name());
        log.info("Successfully find strategy by status IN ({}, {})", firstStatus, secondStatus);
        return strategyByStatusNative;
    }

    @Step("Поиск всех заблокированных и подписанных контрактов")
    @SneakyThrows
    public List<Contract> findAllBlockedContract(Boolean blocked) {
        List<Contract> getBlockedContract = contractRepository.findAllByBlocked(blocked);
        log.info("Successfully find contract {}", getBlockedContract);
        Allure.addAttachment("Найденный контракт", "application/json", objectMapper.writeValueAsString(getBlockedContract));
        return getBlockedContract;
    }

/*    @Step("Поиск всех заблокированных и подписанных контрактов")
    @SneakyThrows
    public List<Contract> findAllContractByState (String state) {
        List<Contract> getContractByState = contractRepository.findAllByState(state);
        log.info("Successfully find contract {}", getContractByState);
        Allure.addAttachment("Найденный контракт", "application/json", objectMapper.writeValueAsString(getContractByState));
        return getContractByState;
    }*/

}
