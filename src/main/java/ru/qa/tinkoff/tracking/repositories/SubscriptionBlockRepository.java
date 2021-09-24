package ru.qa.tinkoff.tracking.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.qa.tinkoff.tracking.entities.SubscriptionBlock;

import javax.transaction.Transactional;
import java.math.BigInteger;
import java.util.Optional;

@Repository
public interface SubscriptionBlockRepository extends JpaRepository<SubscriptionBlock, BigInteger> {


    //находим подписку по номеру договора  ведомого: slave_contract_id
    @Query(nativeQuery = true, value = "select * from subscription_block where subscription_id =:subscriptionId")
    Optional<SubscriptionBlock> findSubscriptionBlockBySubscriptionId(@Param(value = "subscriptionId") Long subscriptionId);


//    //находим подписку по номеру договора и статусу
//    SubscriptionBlock findBySubscriptionBlockById(String id, SubscriptionStatus status);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = "insert into subscription_block(subscription_id, reason, period)"
        + " values (" +
        " :subscription_id," +
        " cast (:reason as subscription_block_reason)," +
        " cast(:period as tsrange)" +
        ")",
        nativeQuery = true)
    int saveSubscriptionBlock(@Param("subscription_id") long subscriptionId,
                              @Param("reason") String reason,
                              @Param("period") String period);

    SubscriptionBlock  findBySubscriptionId (Long subscriptionId);
}
