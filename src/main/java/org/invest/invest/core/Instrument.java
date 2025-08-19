package org.invest.invest.core;

import ru.tinkoff.piapi.core.models.Money;

import java.math.BigDecimal;

public class Instrument {
    private final String name;
    private final BigDecimal quantity;
    private final Money currentPrice;
    private final String type;
    private final String ticker;

    public Instrument(String name, BigDecimal quantity, Money currentPrice, String type, String ticker) {
        this.name = name;
        this.quantity = quantity;
        this.currentPrice = currentPrice;
        this.type = type;
        this.ticker = ticker;
    }

    // Геттеры для всех полей
    public String getName() { return name; }
    public BigDecimal getQuantity() { return quantity; }
    public Money getCurrentPrice() { return currentPrice; }
    public String getType() { return type; }
    public String getTicker() { return ticker; }
}