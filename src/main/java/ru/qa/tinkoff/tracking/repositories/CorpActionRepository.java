package ru.qa.tinkoff.tracking.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.qa.tinkoff.tracking.entities.CorpAction;



import java.util.List;
import java.util.UUID;

public interface CorpActionRepository  extends JpaRepository<CorpAction, UUID> {

    @Query(nativeQuery = true, value = "select * from corp_action.corp_action where strategy_id =:strategyId")
    List<CorpAction> findCorpActionByContractId(@Param(value = "strategyId") UUID strategyId);
}
