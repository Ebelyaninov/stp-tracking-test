package ru.qa.tinkoff.tracking.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;

import javax.transaction.Transactional;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StrategyRepository extends JpaRepository<Strategy, UUID> {

    @Query(nativeQuery = true, value = "select * from tracking.strategy where contract_id =:contractId")
    Optional<Strategy> findStrategyByContractId(@Param(value = "contractId") String contractId);

    @Query(nativeQuery = true, value = "select * from tracking.strategy where contract_id =:contractId")
    List<Strategy> findListStrategyByContractId(@Param(value = "contractId") String contractId);

    @Query("select s from Strategy s where s.description is not null and s.status = :status")
    List<Strategy> findStrategyByStatus(@Param("status") StrategyStatus status);

    // находим 1 запись по стратегии
    @Query(nativeQuery = true, value = "select * from tracking.strategy limit 1")
    Optional<Strategy> selectOneStrategy();

    // находим 1 запись по стратегии
    @Query(nativeQuery = true, value = "select * from tracking.strategy limit 101")
    List<Strategy> selectStrategiesLimit();



    @Query(nativeQuery = true, value = "select * from strategy where status in ('active','draft') " +
        "and position < :position order by position desc limit :limit")
    List<Strategy> findListStrategysByPositionAndLimit(
        @Param(value = "position") Integer position,
        @Param(value = "limit") Integer limit);


    @Query(nativeQuery = true, value = "select * from strategy where status in ('active','draft') " +
        "order by position desc")
    List<Strategy> findListStrategysByOrderPosition();

    @Transactional
    @Modifying(clearAutomatically = true)
    void deleteStrategiesByIdIn(Collection<UUID> ids);

    @Query(nativeQuery = true, value = "select * from strategy where lower(title) = " +
        "lower(:title)")
    Strategy findStrategysByLowerTitle(
        @Param(value = "title") String title);

}
