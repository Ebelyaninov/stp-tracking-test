package ru.qa.tinkoff.tracking.repositories;

import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.SneakyThrows;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;

import javax.transaction.Transactional;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {

    //поиск записи в tracking.client по investId и nickName из SocialProfile
    @Query(nativeQuery = true, value = "select * from tracking.client where id = :id and social_profile ->> 'nickname' = :nickname")
    Optional<Client> findClientByNickname(@Param(value = "id") UUID id, @Param(value = "nickname") String nickname);

    //поиск записи в tracking.client по investId и image из SocialProfile
    @Query(nativeQuery = true, value = "select * from tracking.client where id = :id and social_profile ->> 'image' = :image")
    Optional<Client> findClientByImage(@Param(value = "id") UUID id, @Param(value = "image") String image);


    //поиск записи в tracking.client по investId и image из SocialProfile
    @Query(nativeQuery = true, value = "select * from tracking.client where master_status in ('confirmed', 'registered')" +
        " order by position desc limit :limit")
    List<Client> findClientByMaster(@Param(value = "limit") Integer limit);


    //поиск записи в tracking.client по investId и image из SocialProfile
    @Query(nativeQuery = true, value = "select * from tracking.client where master_status in ('confirmed', 'registered')" +
        " order by position asc limit :limit")
    List<Client> findClientByMasterFirstPosition(@Param(value = "limit") Integer limit);


    @Query(nativeQuery = true, value = "select * from tracking.client where master_status in ('confirmed', 'registered')" +
        "and position < :position order by position desc limit :limit")
    List<Client> findListClientsByMasterByPositionAndLimit(
        @Param(value = "position") Integer position,
        @Param(value = "limit") Integer limit);


    @Transactional
    @Modifying(clearAutomatically = true)
    void deleteClientsByIdIn(Collection<UUID> ids);

    Client findByIdAndMasterStatus(UUID id, ClientStatusType masterStatus);


    @Query(nativeQuery = true, value = "delete from client where id =:id")
    Client deleteClientById(@Param(value = "id") UUID clientId);

    @Query(nativeQuery = true, value = "select * from tracking.client where id = :id")
    List<Client> findListClientsByInvestId (@Param(value = "id") UUID id);

}
