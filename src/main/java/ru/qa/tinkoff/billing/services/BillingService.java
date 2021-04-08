package ru.qa.tinkoff.billing.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.entities.ClientCode;
import ru.qa.tinkoff.billing.entities.InvestAccount;
import ru.qa.tinkoff.billing.repositories.BrokerAccountRepository;
import ru.qa.tinkoff.billing.repositories.ClientCodeRepository;
import ru.qa.tinkoff.billing.repositories.InvestAccountRepository;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class BillingService {

    private final BrokerAccountRepository repository;
    private final InvestAccountRepository repositoryIn;
    private final ClientCodeRepository repositoryCc;
    private final ObjectMapper objectMapper;
    @PersistenceContext
    EntityManager entityManager;


    public BillingService(BrokerAccountRepository repository,
                          InvestAccountRepository repositoryIn,
                          ClientCodeRepository repositoryCc,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.repositoryIn = repositoryIn;
        this.repositoryCc = repositoryCc;
        this.objectMapper = objectMapper;

        //this.entityManager = entityManager;
    }

    public BrokerAccount findById(String id) {
        return repository.findById(id)
            .orElseThrow(EntityNotFoundException::new);
    }

    @SneakyThrows
    @Step("Получение валидного аккаунта")
    public BrokerAccount getFirstValid() {
        BrokerAccount result = repository.findFirstValid();
        Allure.addAttachment("Аккаунт клиента", "application/json", objectMapper.writeValueAsString(result));
        return result;
    }


    @SneakyThrows
    @Step("Получение валидного аккаунта")
    public InvestAccount getInvestValid(UUID investId) {
        return repositoryIn.findById(investId)
            .orElseThrow(EntityNotFoundException::new);
    }


    @SneakyThrows
    @Step("Получение SiebleId  аккаунта c несколькими инвест счетами")
    public String getMultiOpenedInvestIdInvestId() {
        String siebelId = entityManager.createQuery(
            " SELECT ba.investAccount.siebelId from BrokerAccount ba " +
                " WHERE ba.status = 'opened'" +
                " GROUP BY ba.investAccount.siebelId " +
                " HAVING COUNT(1) > 1 ",
            String.class)
            .setMaxResults(1)
            .getSingleResult();
        Allure.addAttachment("SiebleId клиента", "application/json", objectMapper.writeValueAsString(siebelId));
        return siebelId;
    }

    @SneakyThrows
    @Step("Получение SiebleId  аккаунта c несколькими инвест счетами")
    public String getBrokerOpenedInvestIdInvestId() {
        String siebelId = entityManager.createQuery(
            " SELECT ba.investAccount.siebelId from BrokerAccount ba " +
                " WHERE ba.status = 'opened' and ba.type = 'broker'" +
                " GROUP BY ba.investAccount.siebelId " +
                " HAVING COUNT(1) = 1 ",
            String.class)
            .setMaxResults(1)
            .getSingleResult();
        Allure.addAttachment("SiebleId клиента", "application/json", objectMapper.writeValueAsString(siebelId));
        return siebelId;
    }



    @SneakyThrows
    @Step("Получение информации по открытым брокерским счетам аккаунта по siebleId")
    public List<BrokerAccount> getFindValidAccountWithSiebleId(String siebleId) {
        List<BrokerAccount> result = repository.findAllBrokerAccountBySiebleId(siebleId);
        Allure.addAttachment("Данные по аккаунту клиента по siebleId = " + siebleId, "application/json", objectMapper.writeValueAsString(result));
        return result;
    }

    @SneakyThrows
    @Step("Получение информации по открытым не брокерским счетам аккаунта по siebleId")
    public List<BrokerAccount> getfindNotBrokerAccountBySiebleId(String siebleId) {
        List<BrokerAccount> result = repository.findNotBrokerAccountBySiebleId(siebleId);
        Allure.addAttachment("Данные по аккаунту клиента по siebleId = " + siebleId, "application/json", objectMapper.writeValueAsString(result));
        return result;
    }

    @SneakyThrows
    @Step("Получение информации по  не открытым  брокерским счетам аккаунта по siebleId")
    public List<BrokerAccount> getfindNotOpenAccountBySiebleId(String siebleId) {
        List<BrokerAccount> result = repository.findNotOpenAccountBySiebleId(siebleId);
        Allure.addAttachment("Данные по аккаунту клиента по siebleId = " + siebleId, "application/json", objectMapper.writeValueAsString(result));
        return result;
    }



    @SneakyThrows
    @Step("Получение валидного аккаунта со статусом != 'opened'")
    public BrokerAccount getBrokerNoOpenedData() {
        BrokerAccount result = repository.findBrokerNoOpenedData();
        Allure.addAttachment("Аккаунт клиента со статусом != 'opened'", "application/json", objectMapper.writeValueAsString(result));
        return result;
    }

    @SneakyThrows
    @Step("Получение валидного аккаунта с типом != 'broker'")
    public BrokerAccount getNoBrokerData() {
        BrokerAccount result = repository.findNoBrokerData();
        Allure.addAttachment("Аккаунт клиента с типом != 'broker'", "application/json", objectMapper.writeValueAsString(result));
        return result;
    }


    @SneakyThrows
    @Step("Получение информации по  аккаунту по siebleId")
    public List<BrokerAccount> getFindTwoValidContract() {
        List<BrokerAccount> result = repository.findTwoValidContract();
        Allure.addAttachment("Аккаунты валидных клиентов", "application/json", objectMapper.writeValueAsString(result));
        return result;
    }

    @SneakyThrows
    @Step("Получение информации по  clientId по номеру контракта")
    public List<ClientCode> getFindClientCodeByBrokerAccountId(String brokerAccountId) {
        List<ClientCode> result = repositoryCc.findClientCodeByBrokerAccountId(brokerAccountId);
        Allure.addAttachment("Номер clientCode клиента по brokerAccountId", "application/json", objectMapper.writeValueAsString(result));
        return result;

    }

}
