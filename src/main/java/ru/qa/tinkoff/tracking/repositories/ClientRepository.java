package ru.qa.tinkoff.tracking.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Strategy;

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

}
