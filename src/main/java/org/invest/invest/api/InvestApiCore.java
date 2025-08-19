package org.invest.invest.api;

import org.invest.invest.core.InstrumentObj;
import ru.tinkoff.piapi.contract.v1.Account;
import ru.tinkoff.piapi.contract.v1.Instrument;
import ru.tinkoff.piapi.contract.v1.InstrumentStatus;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InvestApiCore {
    private final InvestApi api;
    private final Map<String, CachedInstrument> instrumentCache = new ConcurrentHashMap<>();

    public InvestApiCore(String token) {
        this.api = InvestApi.createReadonly(token);
        warmUpCache();
    }

    public List<Account> getAccounts() {
        return api.getUserService().getAccountsSync();
    }

    public Portfolio getPortfolio(String accountId) {
        return api.getOperationsService().getPortfolioSync(accountId);
    }

    public Account getAccountById(String accountId) {
        return this.getAccounts().stream()
                .filter(acc -> acc.getId().equals(accountId))
                .findFirst()
                .orElse(null);
    }

    public List<InstrumentObj> getInstruments(Portfolio portfolio) {
        List<InstrumentObj> instrumentObjs = new ArrayList<>();
        for (Position position : portfolio.getPositions()) {
            if (position.getQuantity().signum() == 0) continue;
            if (isFiatCurrency(position.getFigi())) {
                instrumentObjs.add(new InstrumentObj(
                        getCurrencyNameByFigi(position.getFigi()),
                        position.getQuantity(),
                        position.getCurrentPrice(),
                        "currency",
                        position.getFigi().substring(0, 3),
                        position.getExpectedYield(),
                        position.getAveragePositionPrice()
                ));
            } else {
                try {
                    Instrument instrumentObjInfo = api.getInstrumentsService().getInstrumentByFigiSync(position.getFigi());
                    instrumentObjs.add(
                            new InstrumentObj(
                                    instrumentObjInfo.getName(),
                                    position.getQuantity(),
                                    position.getCurrentPrice(),
                                    instrumentObjInfo.getInstrumentType(),
                                    instrumentObjInfo.getTicker(),
                                    position.getExpectedYield(),
                                    position.getAveragePositionPrice()
                            ));
                } catch (Exception e) {
                    System.err.println("Could not retrieve instrument details for FIGI: " + position.getFigi() +
                            ". Error: " + e.getMessage());
                }
            }
        }
        instrumentObjs.sort(Comparator.comparing(InstrumentObj::getType).thenComparing(InstrumentObj::getName));
        return instrumentObjs;
    }

    private String getCurrencyNameByFigi(String figi) {
        if (figi == null) return "Неизвестная валюта";
        switch (figi) {
            case "USD800UTSTOM":
            case "USD000UTSTOM":
                return "Доллар США";
            case "RUB000UTSTOM":
                return "Российский рубль";
            case "EUR_RUB__TOM":
            case "EUR000UTSTOM":
                return "Евро";
            case "CNYRUB_TOM":
                return "Китайский юань";
            default:
                return "Валюта";
        }
    }

    private static final Set<String> FIAT_CURRENCY_FIGIS = Set.of(
            "USD800UTSTOM", "USD000UTSTOM",
            "RUB000UTSTOM",
            "EUR_RUB__TOM", "EUR000UTSTOM",
            "CNYRUB_TOM"
    );

    private boolean isFiatCurrency(String figi) {
        return FIAT_CURRENCY_FIGIS.contains(figi);
    }

    private void warmUpCache() {
        System.out.println("Warming up instrument cache...");
        try {
            api.getInstrumentsService().getSharesSync(InstrumentStatus.INSTRUMENT_STATUS_BASE).forEach(share ->
                    instrumentCache.put(share.getFigi(), new CachedInstrument(share.getName(), share.getTicker())));

            api.getInstrumentsService().getBondsSync(InstrumentStatus.INSTRUMENT_STATUS_BASE).forEach(bond ->
                    instrumentCache.put(bond.getFigi(), new CachedInstrument(bond.getName(), bond.getTicker())));

            api.getInstrumentsService().getEtfsSync(InstrumentStatus.INSTRUMENT_STATUS_BASE).forEach(etf ->
                    instrumentCache.put(etf.getFigi(), new CachedInstrument(etf.getName(), etf.getTicker())));

            api.getInstrumentsService().getCurrenciesSync(InstrumentStatus.INSTRUMENT_STATUS_BASE).forEach(currency ->
                    instrumentCache.put(currency.getFigi(), new CachedInstrument(currency.getName(), currency.getTicker())));

        } catch (Exception e) {
            System.err.println("FATAL: Failed to warm up instrument cache. Error: " + e.getMessage());
        }
        System.out.println("Cache warmed up. Total items: " + instrumentCache.size());
    }
}