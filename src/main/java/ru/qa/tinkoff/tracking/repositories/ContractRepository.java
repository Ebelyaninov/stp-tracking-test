package ru.qa.tinkoff.tracking.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.enums.ContractRole;

@Repository
public interface ContractRepository extends JpaRepository<Contract, String> {


    // обновление роли для контракта
    @Query(nativeQuery = true, value = "update tracking.contract set role = :role where id =:id")
    Contract updateRoleInContract(@Param(value = "id") String contractId, @Param("role") ContractRole role);


    // находим 1 запись по договору
    @Query(nativeQuery = true, value = "select * from tracking.contract limit 1")
    Contract selectOneContract();


}
