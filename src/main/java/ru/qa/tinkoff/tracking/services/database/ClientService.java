package ru.qa.tinkoff.tracking.services.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ClientRiskProfile;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.repositories.ClientRepository;

import javax.persistence.EntityNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

//*********** Методы для работы с таблицей client БД postgres@db-tracking.trading.local ***********

@Slf4j
@Service
public class ClientService {
    final ClientRepository clientRepository;
    final ObjectMapper objectMapper;

    public ClientService(ClientRepository clientRepository,
                         ObjectMapper objectMapper) {
        this.clientRepository = clientRepository;
        this.objectMapper = objectMapper;
    }

    public Client getClient(String id) {
        return clientRepository.findById(UUID.fromString(id))
            .orElseThrow(EntityNotFoundException::new);
    }

    @Step("Поиск клиента по id")
    @SneakyThrows
    public Client getClient(UUID clientId) {
        Optional<Client> client = clientRepository.findById(clientId);
        log.info("Successfully find contract {}", clientId);
        Allure.addAttachment("Найденный клиент", "application/json", objectMapper.writeValueAsString(clientId));
        return client.orElseThrow(() -> new RuntimeException("Не найден контракт"));
    }

    @Step("Поиск контракта по id и nickName из SocialProfile")
    @SneakyThrows
    public Optional<Client> getClientByNickname(UUID clientId, String nickName) {
        Optional<Client> client = clientRepository.findClientByNickname(clientId, nickName);
        log.info("Successfully find contract {}", clientId);
        Allure.addAttachment("Найденный клиент", "application/json", objectMapper.writeValueAsString(clientId));
        return client;
    }

    @Step("Поиск контракта по id и nickName из SocialProfile")
    @SneakyThrows
    public Optional<Client> getClientByImage(UUID clientId, String image) {
        Optional<Client> client = clientRepository.findClientByImage(clientId, image);
        log.info("Successfully find contract {}", clientId);
        Allure.addAttachment("Найденный клиент", "application/json", objectMapper.writeValueAsString(clientId));
        return client;
    }

    @Step("Поиск контракта по id")
    @SneakyThrows
    public Optional<Client> findClient(UUID clientId) {
        Optional<Client> client = clientRepository.findById(clientId);
        log.info("Successfully find contract {}", clientId);
        Allure.addAttachment("Найденный клиент", "application/json", objectMapper.writeValueAsString(clientId));
        return client;
    }

    @Step("Удаление клиента")
    @SneakyThrows
    public void deleteClient(Client client) {
        Allure.addAttachment("Удаленный клиент", "application/json", objectMapper.writeValueAsString(client));
        clientRepository.delete(client);
        log.info("Successfully deleted client {}", client.toString());
    }


    @Step("Удаление контракта по id")
    @SneakyThrows
    public Client deleteClientById(UUID clientId) {
        Client client = clientRepository.deleteClientById(clientId);
        log.info("Successfully find client {}", clientId);
        Allure.addAttachment("Удаленый клиент", "application/json", objectMapper.writeValueAsString(clientId));
        return client;
    }

    @Step("Создание клиента для автоследования")
    @SneakyThrows
    public Client createClient(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile, ClientRiskProfile riskProfile) {
        Client client = clientRepository.save(new Client()
            .setId(investId)
            .setMasterStatus(clientStatusType)
            .setSocialProfile(socialProfile)
            .setRiskProfile(riskProfile));
        log.info("Successfully created client {}", client.toString());
        Allure.addAttachment("Клиент", "application/json", objectMapper.writeValueAsString(client));
        return client;
    }


    @Step("Создание клиента для автоследования")
    @SneakyThrows
    public Client createClient1(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile, ClientRiskProfile clientRiskProfile) {
        Client client = clientRepository.save(new Client()
            .setId(investId)
            .setMasterStatus(clientStatusType)
            .setSocialProfile(socialProfile)
            .setRiskProfile(clientRiskProfile));
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

    @Step("Изменение роли контракта")
    public Client updateMasterStatusClient(UUID id, ClientStatusType statusType) throws JsonProcessingException {
        Client client = clientRepository.findById(id).orElseThrow(() -> new RuntimeException("Client not found"));
        client.setMasterStatus(statusType);
        clientRepository.save(client);
        log.info("Successfully update client {}", client);
        Allure.addAttachment("Контракт", "application/json", objectMapper.writeValueAsString(client));
        return client;
    }

    @Step("Удаление клиентов по идентификатору")
    @SneakyThrows
    public void deleteStrategyByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            log.error("Удаление стратегий не выполняется - пустой список идентификаторов стратегий");
        }
        // todo Allure.addAttachment("Удаленная стратегия", "application/json", strategy));
        clientRepository.deleteClientsByIdIn(ids);
        log.info("Successfully deleted clients {}", ids);
    }

    @Step("Поиск клиента по id")
    @SneakyThrows
    public Client getClientByIdAndMasterStatusAndReturnIfFound (UUID clientId, ClientStatusType masterStatus) {
        Client client = clientRepository.findByIdAndMasterStatus (clientId, masterStatus);
        log.info("Successfully find contract {}", clientId);
        Allure.addAttachment("Найденный клиент", "application/json", objectMapper.writeValueAsString(clientId));
        return client;
    }

    @Step("Поиск клиента по статусу")
    @SneakyThrows
    public List<Client> getFindClientByMaster(Integer limit) {
        List<Client> client = clientRepository.findClientByMaster(limit);
        log.info("Successfully find clients {}", limit);
        Allure.addAttachment("Найденные клиенты - ведущие", "application/json", objectMapper.writeValueAsString(client));
        return client;
    }

    @Step("Поиск клиента по статусу с ограничением по курсору")
    @SneakyThrows
    public List<Client> getFindListClientsByMasterByPositionAndLimit(Integer position, Integer limit) {
        List<Client> client = clientRepository.findListClientsByMasterByPositionAndLimit(position, limit);
        log.info("Successfully find clients {}", limit);
        Allure.addAttachment("Найденные клиенты - ведущие", "application/json", objectMapper.writeValueAsString(client));
        return client;
    }

    @Step("Поиск списка клиентов по шinvestId {}")
    @SneakyThrows
    public List<Client> findClinetsByInvestId (UUID investId) {
        List<Client> client = clientRepository.findListClientsByInvestId(investId);
        log.info("Successfully find clients {}", investId);
        Allure.addAttachment("Найденные клиенты - ведущие", "application/json", objectMapper.writeValueAsString(client));
        return client;
    }

    @Step("Поиск клиента по статусу и сортировкой по курсору")
    @SneakyThrows
    public List<Client> getFindClientByMasterFirstPosition(Integer limit) {
        List<Client> client = clientRepository.findClientByMasterFirstPosition(limit);
        log.info("Successfully find clients {}", limit);
        Allure.addAttachment("Найденные клиенты - ведущие", "application/json", objectMapper.writeValueAsString(client));
        return client;
    }
}
