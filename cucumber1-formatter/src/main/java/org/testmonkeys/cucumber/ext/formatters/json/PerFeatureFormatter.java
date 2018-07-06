package org.testmonkeys.cucumber.ext.formatters.json;

import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Step;
import org.testmonkeys.cucumber.ext.formatters.JsonFormatter;

import java.io.File;

/**
 * Cucumber JSON Formatter that will output a report with same format as default json formatter from cucumber, but
 * creating a json file per feature. This helps in situations where the output report is too big due to lots of
 * screenshots.
 */
public class PerFeatureFormatter extends JsonFormatter {
    private boolean inScenarioOutline = false;

    public PerFeatureFormatter(File out) {
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
