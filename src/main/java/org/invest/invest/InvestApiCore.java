package org.invest.invest;


import ru.tinkoff.piapi.contract.v1.Account;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InvestApiCore {
    private final InvestApi api;

    public InvestApiCore(String token) {
        // Рекомендуется использовать readonly токен, если бот не будет торговать
        this.api = InvestApi.createReadonly(token);
    }

    public List<Account> getAccounts() {
        return api.getUserService().getAccountsSync();
    }

    public Account getAccountById(String accountId) {
        return this.getAccounts().stream()
                .filter(acc -> acc.getId().equals(accountId))
                .findFirst()
                .orElse(null);
    }

    public List<Instrument> getInstruments(String accountId) {
        Portfolio portfolio = api.getOperationsService().getPortfolioSync(accountId);
        List<Instrument> instruments = new ArrayList<>();

        for (Position position : portfolio.getPositions()) {
            if (position.getQuantity().signum() == 0) continue;

            // ПРОВЕРЕНО: Используем правильный синхронный метод
            var instrumentInfo = api.getInstrumentsService().getInstrumentByFigiSync(position.getFigi());

            instruments.add(
                    new Instrument(
                            instrumentInfo.getName(),
                            position.getQuantity(),
                            position.getCurrentPrice(),
                            position.getInstrumentType(),
                            instrumentInfo.getTicker()
                    ));
        }
        instruments.sort(Comparator.comparing(Instrument::getType).thenComparing(Instrument::getName));
        return instruments;
    }
}