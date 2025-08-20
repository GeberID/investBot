package org.invest.invest.core.objects;

import ru.tinkoff.piapi.core.models.Money;

import java.math.BigDecimal;

public class InstrumentObj {
    private final String name;
    private final BigDecimal quantity;
    private final Money currentPrice;
    private final String type;
    private final String ticker;
    private final BigDecimal totalProfit;
    private final Money averageBuyPrice;

    public InstrumentObj(String name, BigDecimal quantity, Money currentPrice, String type, String ticker, BigDecimal totalProfit, Money averageBuyPrice) {
        this.name = name;
        this.quantity = quantity;
        this.currentPrice = currentPrice;
        this.type = type;
        this.ticker = ticker;
        this.totalProfit = totalProfit;
        this.averageBuyPrice = averageBuyPrice;
    }

    // Геттеры для всех полей
    public String getName() { return name; }
    public BigDecimal getQuantity() { return quantity; }
    public Money getCurrentPrice() { return currentPrice; }
    public String getType() { return type; }
    public String getTicker() { return ticker; }
    public BigDecimal getTotalProfit() { return totalProfit; }
    public Money getAverageBuyPrice() { return averageBuyPrice; }
}