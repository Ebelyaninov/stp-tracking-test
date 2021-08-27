package ru.qa.tinkoff.tracking.services.database;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.SneakyThrows;


import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.entities.enums.SubscriptionStatus;
import ru.qa.tinkoff.tracking.repositories.SubscriptionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class SubscriptionService {

    final SubscriptionRepository subscriptionRepository;
    final ObjectMapper objectMapper;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                                   ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
    }


    @Step("Поиск подписки по contractId ведомого")
    @SneakyThrows
    public Subscription getSubscriptionByContract (String contractId) {
        Optional<Subscription> subscription = subscriptionRepository.findSubscriptionByContractId(contractId);
        log.info("Successfully find subscription {}", contractId);
        Allure.addAttachment("Найденная подписка", "application/json", objectMapper.writeValueAsString(contractId));
        return subscription.orElseThrow(() -> new RuntimeException("Не найдена подписка"));
    }


    @Step("Поиск подписки по contractId ведомого")
    @SneakyThrows
    public void updateStartTimeSubscriptionById (LocalDateTime startTime, long subscriptionId) {
        subscriptionRepository.updateSubscriptionStartTimeById(startTime, subscriptionId);
        log.info("Successfully find subscription {}", subscriptionId);
        Allure.addAttachment("Найденная подписка", "application/json", objectMapper.writeValueAsString(subscriptionId));
    }



    @Step("Поиск подписки по contractId ведомого")
    @SneakyThrows
    public Optional<Subscription> findSubcription(String contractId) {
        Optional<Subscription> subscription = subscriptionRepository.findSubscriptionByContractId(contractId);
        log.info("Successfully find contract {}", contractId);
        Allure.addAttachment("Найденная подписка", "application/json", objectMapper.writeValueAsString(contractId));
        return subscription;
    }

//    @Step("Поиск подписки по contractId ведомого")
//    @SneakyThrows
//    public Optional<Subscription> findSubscriptionByContractIdAndStatus(String contractId, String status) {
//        Optional<Subscription> subscription = subscriptionRepository.findSubscriptionByContractIdAndStatus(contractId, status);
//        log.info("Successfully find contract {}", contractId);
//        Allure.addAttachment("Найденная подписка", "application/json", objectMapper.writeValueAsString(contractId));
//        return subscription;
//    }

    @Step("Поиск подписки по contractId ведомого")
    @SneakyThrows
    public Subscription findSubscriptionByContractIdAndStatus(String contractId, SubscriptionStatus status) {
        Subscription subscription = subscriptionRepository.findBySlaveContractIdAndStatus(contractId, status);
        log.info("Successfully find contract {}", contractId);
        Allure.addAttachment("Найденная подписка", "application/json", objectMapper.writeValueAsString(contractId));
        return subscription;
    }

    @Step("Поиск стратегии по статусу и не пустому значению Description")
    @SneakyThrows
    public List<Subscription> getSubscriptionByStatus(SubscriptionStatus status) {
        List<Subscription> subscription = subscriptionRepository.findByStatus(status);
        log.info("Successfully find strategy {}", status);
        Allure.addAttachment("Найденная стратегия по статусу", "application/json", objectMapper.writeValueAsString(status));
        return subscription;
    }





    @Step("Поиск подписок по strategyId")
    @SneakyThrows
    public List<Subscription> getSubscriptionByStrategy (UUID strategyId) {
        List<Subscription> subscription = subscriptionRepository.findSubscriptionByStrategyId(strategyId);
        log.info("Successfully find subscription by strategyId {}", strategyId);
        Allure.addAttachment("Найденные подписки по стратегии", "application/json", objectMapper.writeValueAsString(strategyId));
        return subscription;
    }


    @Step("Удаление подписки")
    @SneakyThrows
    public void deleteSubscription(Subscription subscription) {
        Allure.addAttachment("Удаленная подписка", "application/json", objectMapper.writeValueAsString(subscription));
        subscriptionRepository.delete(subscription);
        log.info("Successfully deleted subscription {}", subscription.toString());
    }


    @Step("Сохранение стратегии")
    public Subscription saveSubscription (Subscription subscription) throws JsonProcessingException {
        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Successfully saved subscription {}", saved);
        Allure.addAttachment("Стратегия", "application/json", objectMapper.writeValueAsString(saved));
        return saved;
    }







}
