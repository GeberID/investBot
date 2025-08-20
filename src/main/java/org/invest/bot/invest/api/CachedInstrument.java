package org.invest.bot.invest.api;

public class CachedInstrument {
    private final String name;
    private final String ticker;

    public CachedInstrument(String name, String ticker) {
        this.name = name;
        this.ticker = ticker;
    }

    public String getName() {
        return name;
    }

    public String getTicker() {
        return ticker;
    }
}