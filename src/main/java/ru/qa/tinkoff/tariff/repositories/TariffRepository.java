package ru.qa.tinkoff.tariff.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.qa.tinkoff.tariff.entities.Tariff;

import java.util.List;


@Repository
public interface TariffRepository extends JpaRepository<Tariff, Integer> {

}
