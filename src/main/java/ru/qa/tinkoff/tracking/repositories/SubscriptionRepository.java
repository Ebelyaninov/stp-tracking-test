package ru.qa.tinkoff.tracking.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.SubscriptionStatus;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface SubscriptionRepository extends JpaRepository<Subscription, BigInteger> {
    //находим подписку по номеру договора  ведомого: slave_contract_id
    @Query(nativeQuery = true, value = "select * from tracking.subscription where slave_contract_id =:contractId")
    Optional<Subscription> findSubscriptionByContractId(@Param(value = "contractId") String contractId);

    //находим подписку по номеру договора и статусу
    Subscription findBySlaveContractIdAndStatus(String id, SubscriptionStatus status);

    //находим список подписок по статусу
    List<Subscription> findByStatus(SubscriptionStatus status);


    //находим подписки по номеру стратегии: strategy_id
    @Query(nativeQuery = true, value = "select * from tracking.subscription where strategy_id =:strategyId")
    List<Subscription> findSubscriptionByStrategyId(@Param(value = "strategyId") UUID strategyId);

    @Modifying
    @Query(nativeQuery = true, value = "update tracking.subscription set start_time = :startTime where id =:subscriptionId")
    void updateSubscriptionStartTimeById(@Param("startTime") LocalDateTime startTime,
                                         @Param("subscriptionId") long subscriptionId);

}