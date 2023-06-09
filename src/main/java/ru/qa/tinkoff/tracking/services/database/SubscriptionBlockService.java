package ru.qa.tinkoff.tracking.services.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.tracking.entities.SubscriptionBlock;
import ru.qa.tinkoff.tracking.entities.enums.SubscriptionBlockReason;
import ru.qa.tinkoff.tracking.repositories.SubscriptionBlockRepository;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import static ru.qa.tinkoff.utils.AllureUtils.addJsonAttachment;

@Slf4j
@Service
public class SubscriptionBlockService {
    private final SubscriptionBlockRepository subscriptionBlockRepository;
    final ObjectMapper objectMapper;

    public SubscriptionBlockService(SubscriptionBlockRepository subscriptionBlockRepository,
                                    ObjectMapper objectMapper) {
        this.subscriptionBlockRepository = subscriptionBlockRepository;
        this.objectMapper = objectMapper;
    }


    @Step("Поиск подписки по contractId ведомого")
    @SneakyThrows
    public SubscriptionBlock getSubscriptionBlockBySubscriptionId(Long subscriptionId, String subscriptionBlockReason) {
        Optional<SubscriptionBlock> subscription = subscriptionBlockRepository.findSubscriptionBlockBySubscriptionIdAndReasone(subscriptionId, subscriptionBlockReason);
        log.info("Successfully find subscriptionBlock {}", subscriptionId);
        Allure.addAttachment("Найденная заблокированная подписка", "application/json", objectMapper.writeValueAsString(subscriptionId));
        return subscription.orElseThrow(() -> new RuntimeException("Не найдена заблокированная подписка"));
    }


    @Step("Сохранение заблокированной подписки")
    public SubscriptionBlock saveSubscriptionBlock(long subscriptionId, SubscriptionBlockReason reason, String period, Integer version) throws JsonProcessingException {
        if (version != null) {
            subscriptionBlockRepository
                .saveSubscriptionBlock(subscriptionId, reason.getAlias(), period, version);
        }
        else {
            subscriptionBlockRepository
                .saveSubscriptionBlockWithoutVersion(subscriptionId, reason.getAlias(), period);
        }
        SubscriptionBlock saved = subscriptionBlockRepository
            .findSubscriptionBlockBySubscriptionIdAndReasone(subscriptionId, reason.getAlias())
            .orElseThrow(RuntimeException::new);
        log.info("Successfully saved subscriptionBlock {}", saved);
        Allure.addAttachment("Заблокированная подписка", "application/json", objectMapper.writeValueAsString(saved));
        return saved;
    }


    @Step("Удаление подписки")
    @SneakyThrows
    public void deleteSubscription(SubscriptionBlock subscriptionBlock) {
        Allure.addAttachment("Удаленная блокировка подписки", "application/json", objectMapper.writeValueAsString(subscriptionBlock));
        subscriptionBlockRepository.delete(subscriptionBlock);
        log.info("Successfully deleted subscription {}", subscriptionBlock.toString());
    }


    @Step("Поиск записи в subscription_block по id подписки")
    @SneakyThrows
    public List<SubscriptionBlock> deleteSubscriptionBlockBySubscriptionId (Long subscriptionId) {
        log.info("Получен запрос на удаление записи в subscription_block по id подписки: {} ", subscriptionId);
        List<SubscriptionBlock> subscriptionBlock = subscriptionBlockRepository.findListBySubscriptionId(subscriptionId);
        subscriptionBlockRepository.deleteAll(subscriptionBlock);
        log.info("По subscriptionId: {} найдены записи: {}", subscriptionId, subscriptionBlock);
        addJsonAttachment("Найденные записи: ", subscriptionBlock);
        return subscriptionBlock;
    }




}
