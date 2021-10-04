package ru.qa.tinkoff.tariff.services;

import lombok.extern.slf4j.Slf4j;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.tariff.entities.Tariff;
import ru.qa.tinkoff.tariff.repositories.TariffRepository;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class TariffService {
    private final TariffRepository tariffRepository;

    @Step("Поиск тарифа в БД тарифов")
    @SneakyThrows
    public List <Tariff> getTariff() {
        List <Tariff> tariff = tariffRepository.findAll();
        log.info("Successfully find tariffs  {}", tariff);
        return  tariff;
    }


}
