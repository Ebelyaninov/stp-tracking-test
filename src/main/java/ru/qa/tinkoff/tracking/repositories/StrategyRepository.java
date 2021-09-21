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

    @Query("select s from strategy s where s.description is not null and s.status = :status")
    List<Strategy> findStrategyByStatus(@Param("status") StrategyStatus status);

    @Query(value =
        "select s.* from strategy s " +
        "  join contract cntr on s.contract_id = cntr.id " +
        "  join client cl on cntr.client_id = cl.id" +
        " where s.description is not null and s.status = cast(:status as strategy_status)" +
            " and cl.social_profile is not null", nativeQuery = true)
    List<Strategy> findStrategyByStatusNative(@Param("status") String status);

    @Query(value =
        "select s.id from contract" +
            "  join strategy s on contract.id = s.contract_id " +
            "  join client c on c.id = contract.client_id" +
            " where c.social_profile is not null " +
            "   and s.status = 'active'",
        nativeQuery = true)
    List<Long> findStrategyByStatusWithProfile(@Param("status") String status);

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
