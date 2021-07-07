package ru.qa.tinkoff.tracking.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.qa.tinkoff.tracking.entities.ExchangePosition;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

public interface ExchangePositionRepository extends JpaRepository<ExchangePosition, String> {

    //поиск записи в tracking.exchange_position по ticker
    @Query(nativeQuery = true,
        value = "select * from tracking.exchange_position where ticker = :ticker and trading_clearing_account= :trading_clearing_account")
    Optional<ExchangePosition> findExchangePositionByTicker(
        @Param(value = "ticker") String ticker,
        @Param(value = "trading_clearing_account") String tradingClearingAccount);



    //поиск записи в tracking.exchange_position position и limit
    @Query(nativeQuery = true,
        value = "select * from tracking.exchange_position where ticker || trading_clearing_account > " +
            "(select ticker || trading_clearing_account as filter from tracking.exchange_position where position = :position) " +
            "order by ticker, trading_clearing_account " +
            "limit :limit")
    List<ExchangePosition> findExchangePositionByPositionAndLimit(
        @Param(value = "position") Integer position,
        @Param(value = "limit") Integer limit);


    //поиск записи в tracking.exchange_position position и limit
    @Query(nativeQuery = true,
        value = "select * from tracking.exchange_position order by ticker, trading_clearing_account")
    List<ExchangePosition> findExchangePositionOrderByTickerAndTraAndTradingClearingAccount();



    @Transactional
    @Modifying(clearAutomatically = true)
    void deleteExchangePositionsByTickerAndTradingClearingAccount(String ticker, String tradingClearingAccount);

}
