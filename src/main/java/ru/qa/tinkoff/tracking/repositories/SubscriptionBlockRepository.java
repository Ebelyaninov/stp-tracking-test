package ru.qa.tinkoff.tracking.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.qa.tinkoff.tracking.entities.SubscriptionBlock;

import javax.transaction.Transactional;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionBlockRepository extends JpaRepository<SubscriptionBlock, BigInteger> {


    //находим подписку по номеру договора  ведомого: slave_contract_id
    @Query(value = "select * from subscription_block" +
        " where subscription_id =:subscriptionId AND reason =cast(:reasone as subscription_block_reason)", nativeQuery = true)
    Optional<SubscriptionBlock> findSubscriptionBlockBySubscriptionIdAndReasone (@Param(value = "subscriptionId") long subscriptionId,
                                                                                 @Param("reasone") String reasone);


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

    List<SubscriptionBlock> findListBySubscriptionId (Long subscriptionId);
}
