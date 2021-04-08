package stpTrackingAdminApi.updateExchangePosition;


import extenstions.RestAssuredExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ExchangePositionKafkaService;

@Epic("CreateExchangePosition - Добавление биржевой позиции")
@Feature("TAP-7084")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {KafkaAutoConfiguration.class})
public class KafkaTest {

    @Autowired
    ExchangePositionKafkaService exchangePositionKafkaService;

    @Test
    void teest() {
        exchangePositionKafkaService.findReportExecuteByOrdernum();
    }
}
