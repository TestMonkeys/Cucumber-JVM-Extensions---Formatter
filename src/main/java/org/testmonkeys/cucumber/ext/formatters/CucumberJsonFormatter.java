package org.testmonkeys.cucumber.ext.formatters;

import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Step;

import java.io.File;

public class CucumberJsonFormatter extends JsonFormatter {
    private boolean inScenarioOutline = false;

    public CucumberJsonFormatter(File out) {
        super(out);
    }

    @Override
    public void scenarioOutline(ScenarioOutline scenarioOutline) {
        inScenarioOutline = true;
    }

    @Override
    public void examples(Examples examples) {
        // NoOp
    }

    @Override
    public void startOfScenarioLifeCycle(Scenario scenario) {
        inScenarioOutline = false;
        super.startOfScenarioLifeCycle(scenario);
    }

    @Override
    public void step(Step step) {
        if (!inScenarioOutline) {
            super.step(step);
        }
    }
}
