package ru.qa.tinkoff.tracking.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.qa.tinkoff.tracking.entities.ExchangePosition;

import java.util.Optional;

public interface ExchangePositionRepository extends JpaRepository<ExchangePosition, String> {

    //поиск записи в tracking.exchange_position по ticker
    @Query(nativeQuery = true,
        value = "select * from tracking.exchange_position where ticker = :ticker and trading_clearing_account= :trading_clearing_account")
    Optional<ExchangePosition> findExchangePositionByTicker(
        @Param(value = "ticker") String ticker,
        @Param(value = "trading_clearing_account") String tradingClearingAccount);
}
