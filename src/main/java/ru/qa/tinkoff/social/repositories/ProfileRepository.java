package ru.qa.tinkoff.social.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.qa.tinkoff.social.entities.Profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {


    @Query(nativeQuery = true, value = "select * from profile where siebel_id = ? AND NOT deleted")
    Optional<Profile> findProfileBySiebelId(String siebelId);


    @Query(nativeQuery = true, value = "select * from profile where deleted is not true and has_contract is true" +
        " and image is not null and random() < 0.01 limit 1 AND NOT deleted")
    Optional<Profile> findProfile();

    @Query(nativeQuery = true, value = "select * from profile where deleted is not true and has_contract is false" +
        " and image is not null and random() < 0.01 limit 1 AND NOT deleted")
    Optional<Profile> findProfileNotBroker();

    @Query(nativeQuery = true, value = "select * FROM profile  WHERE nickname = '' AND deleted is not true AND has_contract is true LIMIT 10")
    List<Profile> findProfileEmptyNickname();

}

