package ru.qa.tinkoff.tracking.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractRole;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;

import javax.transaction.Transactional;
import java.util.Collection;
import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, String> {


    // обновление роли для контракта
    @Query(nativeQuery = true, value = "update tracking.contract set role = :role where id =:id")
    Contract updateRoleInContract(@Param(value = "id") String contractId, @Param("role") ContractRole role);


    // находим 1 запись по договору
    @Query(nativeQuery = true, value = "select * from tracking.contract limit 1")
    Contract selectOneContract();

    @Transactional
    @Modifying(clearAutomatically = true)
    void deleteContractsByIdIn(Collection<String> ids);

    List<Contract> findAllByBlocked (Boolean blocked);

 //   List <Contract> findAllByState (String state);

    // находим 1 запись по стратегии
    @Query(nativeQuery = true, value = "SELECT * FROM tracking.contract WHERE blocked = :blocked and id > :cursor ORDER BY id ASC")
    List<Contract> selectContractBlockedLimit(
    @Param(value = "blocked") Boolean blocked,
    @Param(value = "cursor") String cursor);




    // находим 1 запись по договору
    @Query(nativeQuery = true, value = "delete from contract where id =:id")
    Contract deleteContractById(@Param(value = "id") String contractId);

}
