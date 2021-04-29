package ru.qa.tinkoff.billing.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.qa.tinkoff.billing.entities.BrokerAccount;

import java.util.Collection;
import java.util.List;

@Repository
public interface BrokerAccountRepository extends JpaRepository<BrokerAccount, String> {

    BrokerAccount findFirstById(String id);

    /*
        Данный метод реализован с целью упрощения вызовов сущностей, в полях которых
        присутствуют Postgres enum типы
    */

    @Query(nativeQuery = true, value = "select id, invest_id, type, status from account.broker_account " +
        "where type = 'broker' and status = 'opened' and random() < 0.01 limit 1")
    BrokerAccount findFirstValid();

    //получаем список валидных брокерских договоров по значению siebelId
    @Query(value = "SELECT ba FROM BrokerAccount ba WHERE ba.type ='broker' and ba.status = 'opened'" +
        " and ba.investAccount.siebelId=:siebelId")
    List<BrokerAccount> findAllBrokerAccountBySiebelId(@Param(value = "siebelId") String siebelId);


    //получаем список валидных не брокерских договоров по значению siebelId
    @Query(value = "SELECT ba FROM BrokerAccount ba WHERE ba.type <>'broker' and ba.status = 'opened'" +
        " and ba.investAccount.siebelId=:siebelId")
    List<BrokerAccount> findNotBrokerAccountBySiebelId(@Param(value = "siebelId") String siebelId);


    //получаем список закрытых брокерских договоров по значению siebelId
    @Query(value = "SELECT ba FROM BrokerAccount ba WHERE ba.type ='broker' and ba.status <> 'opened'" +
        " and ba.investAccount.siebelId=:siebelId")
    List<BrokerAccount> findNotOpenAccountBySiebelId(@Param(value = "siebelId") String siebelId);


    //находим договор, у которого статус !='opened'
    @Query(nativeQuery = true, value = "select id, invest_id, type, status from account.broker_account " +
        "where type = 'broker' and status != 'opened' and random() < 0.01 limit 1")
    BrokerAccount findBrokerNoOpenedData();

    //находим договор, у которого тип !='broker'
    @Query(nativeQuery = true, value = "select id, invest_id, type, status from account.broker_account " +
        "where  type != 'broker' and status = 'opened' and random() < 0.01 limit 1")
    BrokerAccount findNoBrokerData();


    //находим  2 валидных договора
    @Query(nativeQuery = true, value = "select id, invest_id, type, status from account.broker_account " +
        "where type = 'broker' and status = 'opened' and random() < 0.01 limit 2")
    List<BrokerAccount> findTwoValidContract();

//    @Query(nativeQuery = true, value = "select  ia.siebel_id from account.broker_account as br " +
//        "left join account.invest_account as ia on br.invest_id = ia.id where br.invest_id in" +
//        "(select invest_id from account.broker_account group by invest_id having count(invest_id) > 1)" +
//        "and br.status = 'opened' and  br.type ='broker' limit 1")
//    BrokerAccount multiBrokerOpenedInvestId();



}