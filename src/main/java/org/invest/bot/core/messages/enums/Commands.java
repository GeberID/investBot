package org.invest.core.commands.enums;

public enum Commands {
    start("/start"),
    portfolio("/portfolio");

    private final String command;
    Commands(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }
}
