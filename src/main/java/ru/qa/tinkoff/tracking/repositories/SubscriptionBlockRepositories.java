package ru.qa.tinkoff.tracking.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.qa.tinkoff.tracking.entities.SubscriptionBlock;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionBlockRepositories extends JpaRepository<SubscriptionBlock, Long> {

    SubscriptionBlock  findBySubscriptionId (Long subscriptionId);

}
