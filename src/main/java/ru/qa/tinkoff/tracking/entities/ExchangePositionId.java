package ru.qa.tinkoff.tracking.entities;

import javax.persistence.Column;
import javax.persistence.Id;
import java.io.Serializable;

public class ExchangePositionId implements Serializable {
    String ticker;
    String tradingClearingAccount;
}
