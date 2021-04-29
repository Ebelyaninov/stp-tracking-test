package ru.qa.tinkoff.tracking.services.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.entities.enums.ContractRole;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.repositories.ClientRepository;
import ru.qa.tinkoff.tracking.repositories.ContractRepository;
import ru.qa.tinkoff.tracking.repositories.StrategyRepository;
import ru.qa.tinkoff.tracking.repositories.SubscriptionRepository;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceContext;
import java.util.UUID;

@Slf4j
@Service
public class TrackingService {

    final ClientRepository clientRepository;
    final ContractRepository contractRepository;
    final StrategyRepository strategyRepository;
    final SubscriptionRepository subscriptionRepository;
    final ObjectMapper objectMapper;

    public TrackingService(ClientRepository clientRepository,
                           ContractRepository contractRepository,
                           StrategyRepository strategyRepository,
                           SubscriptionRepository subscriptionRepository,
                           ObjectMapper objectMapper) {
        this.clientRepository = clientRepository;
        this.contractRepository = contractRepository;
        this.strategyRepository = strategyRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
    }

    public Client getClient(String id) {
        return clientRepository.findById(UUID.fromString(id))
            .orElseThrow(EntityNotFoundException::new);
    }

    public Contract getContract(String id) {
        return contractRepository.findById(id)
            .orElseThrow(EntityNotFoundException::new);
    }

    @Step("Удаление клиента")
    @SneakyThrows
    public void deleteClient(Client client) {
        Allure.addAttachment("Удаленный клиент", "application/json", objectMapper.writeValueAsString(client));
        clientRepository.delete(client);
        log.info("Successfully deleted client {}", client.toString());
    }

    @Step("Удаление стратегии")
    @SneakyThrows
    public void deleteStrategy(Strategy strategy) {
        Allure.addAttachment("Удаленная стратегия", "application/json", objectMapper.writeValueAsString(strategy));
        strategyRepository.delete(strategy);
        log.info("Successfully deleted strategy {}", strategy.toString());
    }

    @Step("Удаление контракта")
    @SneakyThrows
    public void deleteContract(Contract contract) {
        Allure.addAttachment("Удаленный контракт", "application/json", objectMapper.writeValueAsString(contract));
        contractRepository.delete(contract);
        log.info("Successfully deleted strategy {}", contract.toString());
    }

    @Step("Создание клиента для автоследования")
    @SneakyThrows
    public Client createClient(UUID investId, ClientStatusType clientStatusType) {
        Client client = clientRepository.save(new Client()
            .setId(investId)
            .setMasterStatus(clientStatusType));
        log.info("Successfully created client {}", client.toString());
        Allure.addAttachment("Клиент", "application/json", objectMapper.writeValueAsString(client));
        return client;
    }

    @Step("Сохранение измененного клиента")
    @SneakyThrows
    public Client saveClient(Client client) {
        Client saved = clientRepository.save(client);
        log.info("Successfully saved client {}", saved);
        Allure.addAttachment("Клиент", "application/json", objectMapper.writeValueAsString(saved));
        return saved;
    }

    @SneakyThrows
    @Step("Сохранение стратегии")
    public Strategy saveStrategy(Strategy strategy) {
        Strategy saved = strategyRepository.saveAndFlush(strategy);
//            save(strategy);
        log.info("Successfully saved strategy {}", saved);
        Allure.addAttachment("Стратегия", "application/json", objectMapper.writeValueAsString(saved));
        return saved;
    }

    @Step("Сохранение контракта")
    public Contract saveContract(Contract contract) throws JsonProcessingException {
        Contract saved = contractRepository.save(contract);
        log.info("Successfully saved contract {}", saved);
        Allure.addAttachment("Контракт", "application/json", objectMapper.writeValueAsString(saved));
        return saved;
    }

    @SneakyThrows
    @Step("Сохранение подписки")
    public Subscription saveSubscription(Subscription subscription) {
        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Successfully saved subscription {}", saved);
        Allure.addAttachment("Подписка", "application/json", objectMapper.writeValueAsString(saved));
        return saved;
    }

    @Step("Изменение роли, статуса и Id стратегии контракта")
    public Contract updateRoleStateStrategyContract(String id, ContractRole role, ContractState state, UUID strategyId) throws JsonProcessingException {
        Contract contract = contractRepository.findById(id).orElseThrow(() -> new RuntimeException("Contract not found"));
        contract.setRole(role);
        contract.setState(state);
        contract.setStrategyId(strategyId);
        contractRepository.save(contract);
        log.info("Successfully update contract {}", contract);
        Allure.addAttachment("Контракт", "application/json", objectMapper.writeValueAsString(contract));
        return contract;
    }

    @Step("Изменение роли контракта")
    public Contract updateRoleContract(String id, ContractRole role) throws JsonProcessingException {
        Contract contract = contractRepository.findById(id).orElseThrow(() -> new RuntimeException("Contract not found"));
        contract.setRole(role);
        contractRepository.save(contract);
        log.info("Successfully update contract {}", contract);
        Allure.addAttachment("Контракт", "application/json", objectMapper.writeValueAsString(contract));
        return contract;
    }
}
