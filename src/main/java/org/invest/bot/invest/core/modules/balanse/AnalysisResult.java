package org.invest.bot.invest.core.modules.balanse;

import java.math.BigDecimal;
import java.util.Map;

public class AnalysisResult {
    public final Map<PortfolioInstrumentStructure, BigDecimal> classDeviations;
    public final ConcentrationProblem concentrationProblems;
    public AnalysisResult(Map<PortfolioInstrumentStructure, BigDecimal> classDeviations, ConcentrationProblem concentrationProblems) {
        this.classDeviations = classDeviations;
        this.concentrationProblems = concentrationProblems;
    }
    public boolean hasDeviations() {
        return !classDeviations.isEmpty() ||
                !concentrationProblems.getConcentrationHumanProblems().isEmpty() ||
                !concentrationProblems.getConcentrationInstrumentProblems().isEmpty();
    }
}
