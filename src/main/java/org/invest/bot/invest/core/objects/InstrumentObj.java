package org.invest.bot.invest.core.objects;

import ru.tinkoff.piapi.contract.v1.Instrument;
import ru.tinkoff.piapi.core.models.Money;
import ru.tinkoff.piapi.core.models.Position;

import java.math.BigDecimal;

public class InstrumentObj {
    private final String name;
    private final String instrumentUid;
    private final BigDecimal quantity;
    private final Money currentPrice;
    private final String type;
    private final String ticker;
    private final BigDecimal totalProfit;
    private final Money averageBuyPrice;
    private final String figi;

    public InstrumentObj(String name, String instrumentUid, BigDecimal quantity, Money currentPrice, String type, String ticker,
                         BigDecimal totalProfit, Money averageBuyPrice, String figi) {
        this.name = name;
        this.instrumentUid = instrumentUid;
        this.quantity = quantity;
        this.currentPrice = currentPrice;
        this.type = type;
        this.ticker = ticker;
        this.totalProfit = totalProfit;
        this.averageBuyPrice = averageBuyPrice;
        this.figi = figi;
    }

    public InstrumentObj(Position position, Instrument instrument) {
        this.name = instrument.getName();
        this.instrumentUid = instrument.getUid();
        this.quantity = position.getQuantity();
        this.currentPrice = position.getCurrentPrice();
        this.type = instrument.getInstrumentType();
        this.ticker = instrument.getTicker();
        this.totalProfit = position.getExpectedYield();
        this.averageBuyPrice = position.getAveragePositionPrice();
        this.figi = position.getFigi();
    }

    public String getName() {
        return name;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Money getCurrentPrice() {
        return currentPrice;
    }

    public String getType() {
        return type;
    }

    public String getTicker() {
        return ticker;
    }

    public BigDecimal getTotalProfit() {
        return totalProfit;
    }

    public Money getAverageBuyPrice() {
        return averageBuyPrice;
    }

    public String getFigi() {
        return figi;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }
}