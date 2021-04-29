package ru.qa.tinkoff.social.services.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.repositories.ProfileRepository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ProfileService {
    final ProfileRepository profileRepository;
    final ObjectMapper objectMapper;

    public ProfileService(ProfileRepository profileRepository,
                          ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.objectMapper = objectMapper;
    }

    @Step("Поиск профайла по siebelId")
    @SneakyThrows
    public Profile getProfileBySiebelId(String siebelId) {
        Optional<Profile> profile = profileRepository.findProfileBySiebelId(siebelId);
        log.info("Successfully find profile {}", siebelId);
        Allure.addAttachment("Найденный профайл клиента", "application/json", objectMapper.writeValueAsString(siebelId));
        return profile.orElseThrow(() -> new RuntimeException("Не найден профайл клиента"));
    }

    @Step("Поиск профайла c брокерским счетом")
    @SneakyThrows
    public Profile getProfile() {
        Optional<Profile> profile = profileRepository.findProfile();
        log.info("Successfully find profile {}");
        Allure.addAttachment("Найденный профайл клиента", "application/json", objectMapper.writeValueAsString(profile));
        return profile.orElseThrow(() -> new RuntimeException("Не найден профайл клиента"));
    }

    @Step("Поиск профайла c не брокерским счетом")
    @SneakyThrows
    public Profile getProfileNotBroker() {
        Optional<Profile> profile = profileRepository.findProfileNotBroker();
        log.info("Successfully find profile {}");
        Allure.addAttachment("Найденный профайл клиента", "application/json", objectMapper.writeValueAsString(profile));
        return profile.orElseThrow(() -> new RuntimeException("Не найден профайл клиента"));
    }

    @SneakyThrows
    @Step("Получение валидного аккаунта")
    public List<Profile> getProfileEmptyNickname() {
        List<Profile> result = profileRepository.findProfileEmptyNickname();
        log.info("Successfully find profile with empty Nickname {}");
        Allure.addAttachment("Профайл клиента с пустым Nickname", "application/json", objectMapper.writeValueAsString(result));
        return result;
    }
}
