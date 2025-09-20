package org.invest.bot.invest.core.objects;

import ru.tinkoff.piapi.contract.v1.Instrument;
import ru.tinkoff.piapi.contract.v1.MoneyValue;
import ru.tinkoff.piapi.contract.v1.PortfolioPosition;
import ru.tinkoff.piapi.contract.v1.Quotation;

public class InstrumentObj {
    private final String name;
    private final String instrumentUid;
    private final Quotation quantity;
    private final MoneyValue currentPrice;
    private final String type;
    private final String ticker;
    private final Quotation totalProfit;
    private final MoneyValue averageBuyPrice;
    private final String figi;
    private final Boolean getBuyAvailableFlag;
    private final int lot;
    private final Quotation expectedYield;
    private final MoneyValue averagePositionPrice;

    public InstrumentObj(PortfolioPosition position, Instrument instrument, int lot) {
        this.name = instrument.getName();
        this.instrumentUid = instrument.getUid();
        this.quantity = position.getQuantity();
        this.currentPrice = position.getCurrentPrice();
        this.type = instrument.getInstrumentType();
        this.ticker = instrument.getTicker();
        this.totalProfit = position.getExpectedYield();
        this.averageBuyPrice = position.getAveragePositionPrice();
        this.figi = position.getFigi();
        this.getBuyAvailableFlag = instrument.getBuyAvailableFlag();
        this.lot = lot;
        this.expectedYield = position.getExpectedYield();
        this.averagePositionPrice = position.getAveragePositionPrice();
    }

    public String getName() {
        return name;
    }

    public Quotation getQuantity() {
        return quantity;
    }

    public MoneyValue getCurrentPrice() {
        return currentPrice;
    }

    public String getType() {
        return type;
    }

    public String getTicker() {
        return ticker;
    }

    public Quotation getTotalProfit() {
        return totalProfit;
    }

    public MoneyValue getAverageBuyPrice() {
        return averageBuyPrice;
    }

    public String getFigi() {
        return figi;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public Boolean getGetBuyAvailableFlag() {
        return getBuyAvailableFlag;
    }

    public int getLot() {
        return lot;
    }

    public Quotation getExpectedYield() {
        return expectedYield;
    }

    public MoneyValue getAveragePositionPrice() {
        return averagePositionPrice;
    }
}