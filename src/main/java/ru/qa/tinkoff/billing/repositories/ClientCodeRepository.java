package ru.qa.tinkoff.billing.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.qa.tinkoff.billing.entities.ClientCode;


import java.util.List;


@Repository
public interface ClientCodeRepository extends JpaRepository<ClientCode, String> {

    // получаем номер clientCode по значению brokerAccountId
    @Query(nativeQuery = true, value = "select * from account.client_code where broker_account_id=:broker_account_id ")
    List<ClientCode> findClientCodeByBrokerAccountId(@Param(value = "broker_account_id") String brokerAccountId);



}



