package org.invest.bot.invest.core.modules.balanse;

import org.invest.bot.invest.core.objects.InstrumentObj;

import java.util.HashMap;
import java.util.List;

public class ConcentrationProblem {
    private List<String> concentrationHumanProblems;
    private List<ActualDistribution> concentrationInstrumentProblems;

    public ConcentrationProblem(List<String> concentrationHumanProblems, List<ActualDistribution> concentrationInstrumentProblems) {
        this.concentrationHumanProblems = concentrationHumanProblems;
        this.concentrationInstrumentProblems = concentrationInstrumentProblems;
    }

    public List<String> getConcentrationHumanProblems() {
        return concentrationHumanProblems;
    }

    public List<ActualDistribution> getConcentrationInstrumentProblems() {
        return concentrationInstrumentProblems;
    }
}
