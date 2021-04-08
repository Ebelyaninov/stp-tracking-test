package ru.qa.tinkoff.investTracking.services;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MasterPortfolioDaoAdapter {
    private final MasterPortfolioDao masterPortfolioDao;

    public MasterPortfolio getLatestMasterPortfolio(String contractId, UUID strategyId) {
        try {
            return masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
