package org.invest.bot.core.messages.enums;

public enum Commands {
    start("/start"),
    portfolio("/portfolio"),
    analyze("/analyze"),
    exp("/exp"),
    instrument("/instrument");

    private final String command;
    Commands(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }
}
