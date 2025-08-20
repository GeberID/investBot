package org.invest.invest.core.modules.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.invest.invest.api.InvestApiCore;
import org.invest.invest.core.modules.balanse.BalanceModuleConf;
import org.invest.invest.core.modules.balanse.BalanceService;
import org.invest.invest.core.objects.InstrumentObj;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.Account;
import ru.tinkoff.piapi.core.models.Portfolio;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class AiReportService {

    private final InvestApiCore apiCore;
    private final BalanceService balanceService;
    private final ObjectMapper objectMapper; // Jackson's main object

    public AiReportService(InvestApiCore apiCore, BalanceService balanceService) {
        this.apiCore = apiCore;
        this.balanceService = balanceService;
        this.objectMapper = new ObjectMapper();
    }

    public File generateReportFile() throws IOException {
        // Предполагаем, что работаем с первым счетом
        Account account = apiCore.getAccounts().get(0);
        Portfolio portfolio = apiCore.getPortfolio(account.getId());
        List<InstrumentObj> instruments = apiCore.getInstruments(portfolio);

        // 1. Создаем корневой JSON-объект
        ObjectNode rootNode = objectMapper.createObjectNode();

        // 2. Заполняем все секции
        rootNode.put("export_date", Instant.now().toString());
        addAccountInfo(rootNode, account);
        addPortfolioSummary(rootNode, portfolio);
        addStrategicAllocation(rootNode, portfolio, instruments);
        addInstrumentsDetails(rootNode, instruments);

        // 3. Создаем временный файл и записываем в него JSON
        File tempFile = File.createTempFile("portfolio_report_", ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile, rootNode);

        return tempFile;
    }

    private void addAccountInfo(ObjectNode root, Account account) {
        ObjectNode info = root.putObject("account_info");
        info.put("account_id", account.getId());
        info.put("account_name", account.getName());
    }

    private void addPortfolioSummary(ObjectNode root, Portfolio portfolio) {
        ObjectNode summary = root.putObject("portfolio_summary");
        ObjectNode totalValue = summary.putObject("total_value");
        totalValue.put("amount", portfolio.getTotalAmountPortfolio().getValue());
        totalValue.put("currency", portfolio.getTotalAmountPortfolio().getCurrency());
        summary.put("total_profit_percentage", portfolio.getExpectedYield());
    }

    private void addStrategicAllocation(ObjectNode root, Portfolio portfolio, List<InstrumentObj> instruments) {
        // --- ИСПРАВЛЕНИЕ: Вызываем новый, правильный метод ---
        Map<BalanceModuleConf, BigDecimal> distribution = balanceService.calculateActualDistribution(portfolio, instruments);

        ObjectNode allocation = root.putObject("strategic_allocation_actual");
        for (Map.Entry<BalanceModuleConf, BigDecimal> entry : distribution.entrySet()) {
            // Преобразуем имя enum в более удобный для JSON формат
            String key = entry.getKey().name()
                    .toLowerCase()
                    .replace("target_", "")
                    .replace("__", "_"); // Для "SATELLITE__PERCENTAGE"
            allocation.put(key, entry.getValue());
        }
    }

    private void addInstrumentsDetails(ObjectNode root, List<InstrumentObj> instruments) {
        ArrayNode instrumentsNode = root.putArray("instruments");
        for (InstrumentObj inst : instruments) {
            ObjectNode instNode = instrumentsNode.addObject();
            instNode.put("name", inst.getName());
            instNode.put("ticker", inst.getTicker());
            instNode.put("figi", inst.getFigi());
            instNode.put("type", inst.getType());
            instNode.put("quantity", inst.getQuantity()); // <-- ДОБАВЛЕНО
            instNode.put("currency", inst.getCurrentPrice().getCurrency());
            instNode.put("current_price_per_unit", inst.getCurrentPrice().getValue());
            instNode.put("total_current_value", inst.getCurrentPrice().getValue().multiply(inst.getQuantity()));

            if (inst.getAverageBuyPrice() != null && inst.getTotalProfit() != null) {
                instNode.put("average_buy_price", inst.getAverageBuyPrice().getValue());
                instNode.put("total_profit_absolute", inst.getTotalProfit());

                BigDecimal totalInvested = inst.getAverageBuyPrice().getValue().multiply(inst.getQuantity());
                if (totalInvested.signum() != 0) {
                    BigDecimal profitPercentage = inst.getTotalProfit()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(totalInvested, 2, RoundingMode.HALF_UP);
                    instNode.put("total_profit_percentage", profitPercentage);
                } else {
                    instNode.put("total_profit_percentage", 0);
                }
            }
        }
    }
}