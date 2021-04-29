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
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.repositories.ClientRepository;

import javax.persistence.EntityNotFoundException;
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

    @Step("Создание клиента для автоследования")
    @SneakyThrows
    public Client createClient(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile) {
        Client client = clientRepository.save(new Client()
            .setId(investId)
            .setMasterStatus(clientStatusType)
            .setSocialProfile(socialProfile));
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
}
