package org.invest.bot.invest.api;

import org.invest.bot.invest.core.objects.InstrumentObj;
import ru.tinkoff.piapi.contract.v1.*;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

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

    public List<HistoricCandle> getDailyCandles(int days, String instrumentId){
        Instant now = Instant.now();
        Instant start = now.minus(days,ChronoUnit.DAYS);
        return api.getMarketDataService().getCandlesSync(instrumentId,now,start,CandleInterval.CANDLE_INTERVAL_DAY);
    }

    public List<HistoricCandle> getWeeklyCandles(int weeks, String instrumentId){
        Instant now = Instant.now();
        Instant start = now.minus(weeks,ChronoUnit.WEEKS);
        return api.getMarketDataService().getCandlesSync(instrumentId,now,start,CandleInterval.CANDLE_INTERVAL_WEEK);
    }

    public List<Dividend> getDividends(String instrumentFigi){
        Instant now = Instant.now();
        Instant yearAhead = now.plus(365, ChronoUnit.DAYS);
        return api.getInstrumentsService().getDividendsSync(instrumentFigi, now, yearAhead);
    }

    public Instrument getInstrumentInfo (InstrumentObj instrumentObj) throws ExecutionException, InterruptedException {
        Instrument instrument = api.getInstrumentsService().getInstrumentByFigiSync(instrumentObj.getFigi());
        return instrument;
    }

    public List<InstrumentObj> getInstruments(Portfolio portfolio) {
        List<InstrumentObj> instrumentObjs = new ArrayList<>();
        for (Position position : portfolio.getPositions()) {
            try {
                instrumentObjs.add(new InstrumentObj(position,
                        api.getInstrumentsService().getInstrumentByFigiSync(position.getFigi())));
            } catch (Exception e) {
                System.err.println("Could not retrieve instrument details for FIGI: " + position.getFigi() +
                        ". Error: " + e.getMessage());
            }
        }
        instrumentObjs.sort(Comparator.comparing(InstrumentObj::getType).thenComparing(InstrumentObj::getName));
        return instrumentObjs;
    }

    public InstrumentObj getInstrument(Portfolio portfolio, String instrumentTicket){
        return getInstruments(portfolio).stream().filter(f -> f.getTicker().equals(instrumentTicket)).findFirst().orElse(null);
    }

    public Position getPortfolioPosition(String accountId,String figi){
        Portfolio portfolio = getPortfolio(accountId);
        List<Position> positions = portfolio.getPositions();
        return positions.stream()
                .filter(p -> p.getFigi().equalsIgnoreCase(figi)) // Ищем по тикеру, игнорируя регистр
                .findFirst()
                .orElse(null);
    }

    public List<Operation> getOperationsForLastMonth(String accountId) {
        Instant now = Instant.now();
        Instant monthAgo = now.minus(30, ChronoUnit.DAYS);
        return api.getOperationsService().getExecutedOperationsSync(accountId, monthAgo, now);
    }

    /**
     * НОВЫЙ ПУБЛИЧНЫЙ МЕТОД
     * Получает базовую информацию об инструменте по его FIGI.
     * Этот метод НЕ заполняет данные, специфичные для портфеля (количество, цена покупки, профит).
     * Он нужен как справочник для получения имени и тикера по FIGI.
     *
     * @param figi FIGI-идентификатор инструмента.
     * @return Объект InstrumentObj с базовой информацией или null, если инструмент не найден.
     */
    public InstrumentObj getInstrumentByFigi(String figi) {
        // Проверка на случай, если FIGI пустой (например, для операций пополнения счета)
        if (figi == null || figi.isEmpty()) {
            return null;
        }
        try {
            return new InstrumentObj(
                    null,api.getInstrumentsService().getInstrumentByFigiSync(figi));
        } catch (Exception e) {

            System.err.println("Не удалось получить информацию по FIGI: " + figi + ". Ошибка: " + e.getMessage());
            return null;
        }
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