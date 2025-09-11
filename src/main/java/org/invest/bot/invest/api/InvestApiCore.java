package org.invest.bot.invest.api;

import lombok.extern.slf4j.Slf4j;
import org.invest.bot.invest.core.modules.instruments.IndicatorType;
import org.invest.bot.invest.core.modules.instruments.WhiteListOfShares;
import org.invest.bot.invest.core.objects.InstrumentObj;
import org.springframework.stereotype.Component;
import ru.tinkoff.piapi.contract.v1.*;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.TypeOfPrice.TYPE_OF_PRICE_CLOSE;

@Component
@Slf4j
public class InvestApiCore {
    private final InvestApi api;

    public InvestApiCore(String token) {
        this.api = InvestApi.createReadonly(token);
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

    public List<Dividend> getDividends(String instrumentFigi){
        Instant now = Instant.now();
        Instant yearAhead = now.plus(365, ChronoUnit.DAYS);
        List<Dividend> dividends = api.getInstrumentsService().getDividendsSync(instrumentFigi, now, yearAhead);
        if(dividends == null){
            return new ArrayList<>();
        }
        return dividends;
    }

    public GetTechAnalysisResponse getTechAnalysis(
            InstrumentObj instrument,
            IndicatorType indicatorType) {
        Instant to = Instant.now();
        Instant from = to.minus(indicatorType.getHistoryDays(),ChronoUnit.DAYS);
        try {
            return api.getMarketDataService().getTechAnalysis(indicatorType.getApiType(),
                    instrument.getInstrumentUid(),
                    from,
                    to,
                    indicatorType.getInterval(),
                    TYPE_OF_PRICE_CLOSE,
                    indicatorType.getLength(),
                    indicatorType.getDeviation(),
                    indicatorType.getSmoothingFastLength(),
                    indicatorType.getSmoothingSlowLength(),
                    indicatorType.getSmoothingSignal()).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<InstrumentObj> getInstruments(Portfolio portfolio) {
        List<InstrumentObj> instrumentObjs = new ArrayList<>();
        for (Position position : portfolio.getPositions()) {
            try {
                if(position.getInstrumentType().equals("share")){
                    instrumentObjs.add(new InstrumentObj(position,
                            api.getInstrumentsService().getInstrumentByFigiSync(position.getFigi()),
                            WhiteListOfShares.getCorrectLot(position.getFigi())));
                }else {
                    instrumentObjs.add(new InstrumentObj(position,
                            api.getInstrumentsService().getInstrumentByFigiSync(position.getFigi()),1));
                }

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
     * Получает полную информацию об инструменте по его FIGI.
     * Использует внутренний кэш SDK для эффективности.
     * @param figi FIGI инструмента
     * @return Объект Instrument или null, если не найден.
     */
    public Instrument getInstrumentByFigi(String figi) {
        try {
            return api.getInstrumentsService().getInstrumentByFigiSync(figi);
        } catch (Exception e) {
            log.error("Не удалось найти инструмент по FIGI: {}", figi, e);
            return null;
        }
    }

    /**
     * Универсальный метод для поиска ЛЮБОГО инструмента по его тикеру.
     * Сначала находит инструмент через общий поиск, а затем получает
     * полную информацию по найденному FIGI.
     * @param ticker Тикер инструмента (напр. "SBER", "TMON@", "GLDRUB_TOM")
     * @return Объект Instrument или null, если не найден.
     */
    public Instrument getInstrumentByTicker(String ticker) {
        try {
            // Шаг 1: Используем универсальный поиск, который вернет список совпадений.
            List<InstrumentShort> searchResult = api.getInstrumentsService().findInstrumentSync(ticker);

            // Шаг 2: Если ничего не найдено, возвращаем null.
            if (searchResult.isEmpty()) {
                log.warn("Инструмент с тикером '{}' не найден.", ticker);
                return null;
            }

            // Шаг 3: Берем первый результат (обычно самый релевантный) и получаем его FIGI.
            String figi = searchResult.get(0).getFigi();

            // Шаг 4: Используем уже существующий у вас надежный метод getInstrumentByFigi,
            // чтобы получить полную информацию. Это предотвращает дублирование кода.
            return getInstrumentByFigi(figi);

        } catch (Exception e) {
            log.error("Ошибка при поиске инструмента по тикеру: {}", ticker, e);
            return null;
        }
    }

    /**
     * Получает последние цены для списка инструментов по их FIGI.
     * @param figis Список FIGI
     * @return Карта [FIGI -> Quotation], содержащая цены.
     */
    public Map<String, Quotation> getLastPrices(List<String> figis) {
        if (figis == null || figis.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return api.getMarketDataService().getLastPricesSync(figis)
                    .stream()
                    .collect(Collectors.toMap(LastPrice::getFigi, LastPrice::getPrice));
        } catch (Exception e) {
            log.error("Не удалось получить последние цены для списка FIGI.", e);
            return new HashMap<>(); // Возвращаем пустую карту в случае ошибки
        }
    }
}