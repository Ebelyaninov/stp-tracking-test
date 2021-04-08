package ru.qa.tinkoff.tracking.steps;

import com.google.protobuf.Timestamp;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.tinkoff.trading.tracking.Tracking;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StpTrackingMasterSteps {

    private final ContractService contractService;
    private final TrackingService trackingService;
    private final ClientService clientService;


    //создаем в БД tracking данные по ведущему: client, contract, strategy
    @Step("Создать договор и стратегию в бд автоследования для ведущего {client}")
    @SneakyThrows
    public void createClientWithContractAndStrategy(Client clientMaster, Contract contractMaster, Strategy strategy, UUID investId,
                                             String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                             ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
        //создаем запись о клиенте в tracking.client
//        clientMaster = clientService.createClient(investId, ClientStatusType.registered, null);
        // создаем запись о договоре клиента в tracking.contract
        contractMaster = new Contract()
            .setId(contractId)
            .setClientId(clientMaster.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contractMaster = contractService.saveContract(contractMaster);
        //создаем запись о стратегии клиента
        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contractMaster)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date);
        strategy = trackingService.saveStrategy(strategy);
    }


    // создаем команду в топик кафка tracking.master.command
    public Tracking.PortfolioCommand createActualizeCommandToTrackingMasterCommand(String contractId, OffsetDateTime time, int version,
                                                                            long unscaled, int scale, long unscaledBaseMoney, int scaleBaseMoney,
                                                                            Tracking.Portfolio.Action action, Tracking.Decimal price,
                                                                            Tracking.Decimal quantityS, String ticker, String tradingClearingAccount) {
        Tracking.Decimal quantity = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaled)
            .setScale(scale)
            .build();
        Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaledBaseMoney)
            .setScale(scaleBaseMoney)
            .build();
        Tracking.Portfolio.Position position = Tracking.Portfolio.Position.newBuilder()
            .setTicker(ticker)
            .setTradingClearingAccount(tradingClearingAccount)
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(action).build())
            .setQuantity(quantity)
            .build();
        Tracking.PortfolioCommand command;
        Tracking.Portfolio.BaseMoneyPosition baseMoneyPosition = Tracking.Portfolio.BaseMoneyPosition.newBuilder()
            .setQuantity(quantityBaseMoney)
            .build();
        Tracking.Portfolio portfolio = Tracking.Portfolio.newBuilder()
            .setVersion(version)
            .addPosition(position)
            .setBaseMoneyPosition(baseMoneyPosition)
            .build();
        Tracking.Signal signal = Tracking.Signal.newBuilder()
            .setPrice(price)
            .setQuantity(quantityS)
            .build();
        command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractId)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(portfolio)
            .setSignal(signal)
            .build();
        return command;
    }

}
