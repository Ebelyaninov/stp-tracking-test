package ru.qa.tinkoff.tracking.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.qa.tinkoff.tracking.entities.Dividend;
import ru.qa.tinkoff.tracking.entities.Subscription;


import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DividendRepository extends JpaRepository<Dividend, BigInteger> {


    @Query(nativeQuery = true, value = "select * from corp_action.subscription where strategy_id =:strategyId")
    List<Dividend> findDividendByStrategyId(@Param(value = "strategyId") UUID strategyId);
}
