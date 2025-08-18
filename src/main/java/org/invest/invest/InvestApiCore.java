package org.invest.invest;

import ru.tinkoff.piapi.contract.v1.Account;
import ru.tinkoff.piapi.contract.v1.InstrumentShort;
import ru.tinkoff.piapi.contract.v1.InstrumentStatus; // <-- 1. ДОБАВЛЕН ВАЖНЫЙ ИМПОРТ
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InvestApiCore {
    private final InvestApi api;

    // --- ИСПРАВЛЕНО: Кэш теперь хранит наши собственные объекты CachedInstrument ---
    private final Map<String, CachedInstrument> instrumentCache = new ConcurrentHashMap<>();

    public InvestApiCore(String token) {
        this.api = InvestApi.createReadonly(token);
        warmUpCache();
    }

    private void warmUpCache() {
        System.out.println("Warming up instrument cache...");
        try {
            // --- ИСПРАВЛЕНО: Создаем наш CachedInstrument из каждого Share ---
            api.getInstrumentsService().getSharesSync(InstrumentStatus.INSTRUMENT_STATUS_BASE).forEach(share ->
                    instrumentCache.put(share.getFigi(), new CachedInstrument(share.getName(), share.getTicker())));

            // --- ИСПРАВЛЕНО: Создаем наш CachedInstrument из каждого Bond ---
            api.getInstrumentsService().getBondsSync(InstrumentStatus.INSTRUMENT_STATUS_BASE).forEach(bond ->
                    instrumentCache.put(bond.getFigi(), new CachedInstrument(bond.getName(), bond.getTicker())));

            // --- ИСПРАВЛЕНО: Создаем наш CachedInstrument из каждого Etf ---
            api.getInstrumentsService().getEtfsSync(InstrumentStatus.INSTRUMENT_STATUS_BASE).forEach(etf ->
                    instrumentCache.put(etf.getFigi(), new CachedInstrument(etf.getName(), etf.getTicker())));

            // --- ИСПРАВЛЕНО: Создаем наш CachedInstrument из каждой Currency ---
            api.getInstrumentsService().getCurrenciesSync(InstrumentStatus.INSTRUMENT_STATUS_BASE).forEach(currency ->
                    instrumentCache.put(currency.getFigi(), new CachedInstrument(currency.getName(), currency.getTicker())));

        } catch (Exception e) {
            // Здесь должен быть ваш логгер
            System.err.println("FATAL: Failed to warm up instrument cache. Error: " + e.getMessage());
        }
        System.out.println("Cache warmed up. Total items: " + instrumentCache.size());
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

            // Теперь мы получаем наш собственный объект из кэша
            CachedInstrument instrumentInfo = instrumentCache.get(position.getFigi());

            if (instrumentInfo != null) {
                // И используем его данные для создания объекта Instrument
                instruments.add(
                        new Instrument(
                                instrumentInfo.getName(),
                                position.getQuantity(),
                                position.getCurrentPrice(),
                                position.getInstrumentType(),
                                instrumentInfo.getTicker()
                        ));
            } else {
                System.err.println("WARN: Instrument with FIGI " + position.getFigi() + " not found in cache.");
            }
        }
        instruments.sort(Comparator.comparing(Instrument::getType).thenComparing(Instrument::getName));
        return instruments;
    }
}