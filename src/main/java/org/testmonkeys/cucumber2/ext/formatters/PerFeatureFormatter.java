package org.testmonkeys.cucumber2.ext.formatters;


import cucumber.api.*;
import cucumber.api.event.*;
import cucumber.api.formatter.Formatter;
import gherkin.AstBuilder;
import gherkin.Parser;
import gherkin.ParserException;
import gherkin.TokenMatcher;
import gherkin.ast.*;
import gherkin.deps.com.google.gson.Gson;
import gherkin.deps.com.google.gson.GsonBuilder;
import gherkin.deps.net.iharder.Base64;
import gherkin.pickles.Argument;
import gherkin.pickles.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;

import static org.testmonkeys.cucumber2.ext.formatters.Nodes.*;

public class PerFeatureFormatter implements Formatter {

    private static Map<String, TestSourceRead> readEventMap = new HashMap<>();
    private final File outFolder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Map<String, Object> backgroundMap;
    private Map<String, Object> currentElementMap;
    private Map<String, Object> currentTestCaseMap;
    private List<Map<String, Object>> currentStepsList;
    private Map<String, Object> currentStepOrHookMap;
    private Map<String, Object> currentBeforeStepHookList = new HashMap<>();
    private Map<String, Object> currentFeatureMap;
    private Feature currentFeature;
    private Background background;
    private String currentFeatureUri;
    private ScenarioDefinition currentScenario;
    private int scenarioCount;

    public PerFeatureFormatter(File outFolder) {
        this.outFolder = outFolder;
    }

    private static Background getBackgroundForTestCase(Feature feature) {
        ScenarioDefinition backgound = feature.getChildren().get(0);
        if (backgound instanceof Background) {
            return (Background) backgound;
        } else {
            return null;
        }
    }

    /*
     * Handle methods
     * */

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestSourceRead.class, this::handleTestSourceRead);
        publisher.registerHandlerFor(TestCaseStarted.class, this::handleTestCaseStarted);
        publisher.registerHandlerFor(TestStepStarted.class, this::handleTestStepStarted);
        publisher.registerHandlerFor(TestStepFinished.class, this::handleTestStepFinished);
        publisher.registerHandlerFor(TestRunFinished.class, event -> finishReport());
        publisher.registerHandlerFor(EmbedEvent.class, this::handleEmbedding);
        publisher.registerHandlerFor(WriteEvent.class, this::handleWrite);
    }

    private void handleTestSourceRead(TestSourceRead event) {
        readEventMap.put(event.uri, event);
    }

    private void handleTestCaseStarted(TestCaseStarted event) {
        List<Map<String, Object>> currentElementsList;
        if (currentFeature == null || !currentFeatureUri.equals(event.testCase.getUri())) {
            if (currentFeatureUri != null) {
                flushCurrentFeature();
            }
            currentFeatureUri = event.testCase.getUri();
            currentFeature = getFeature(readEventMap.get(event.testCase.getUri()).source);
            currentFeatureMap = createFeatureMap(currentFeature, event.testCase.getUri());

            background = getBackgroundForTestCase(currentFeature);
            if (background != null)
                backgroundMap = createBackground(background);
            else backgroundMap.clear();
        }

        currentElementsList = (List<Map<String, Object>>) currentFeatureMap.get(ELEMENTS.value);

        currentTestCaseMap = createTestCase(event.testCase);

        if (backgroundMap != null && !backgroundMap.isEmpty()) {
            backgroundMap.put(STEPS.value, new ArrayList<Map<String, Object>>());
            currentElementMap = backgroundMap;
            currentElementsList.add(currentElementMap);
        } else
            currentElementMap = currentTestCaseMap;

        currentElementsList.add(currentTestCaseMap);
        currentStepsList = (List<Map<String, Object>>) currentElementMap.get(STEPS.value);
    }

    private void handleTestStepStarted(TestStepStarted event) {

        if (event.testStep instanceof PickleStepTestStep) {
            PickleStepTestStep testStep = (PickleStepTestStep) event.testStep;
            if (isFirstStepAfterBackground(testStep)) {
                currentElementMap = currentTestCaseMap;
                currentStepsList = (List<Map<String, Object>>) currentElementMap.get(STEPS.value);
            }
            currentStepOrHookMap = createTestStep(testStep);

            if (currentBeforeStepHookList.containsKey(HookType.Before.toString())) {
                currentStepOrHookMap.put(HookType.Before.toString(), currentBeforeStepHookList.get(HookType.Before.toString()));
                currentBeforeStepHookList.clear();
            }
            currentStepsList.add(currentStepOrHookMap);
        } else if (event.testStep instanceof HookTestStep) {
            HookTestStep hookTestStep = (HookTestStep) event.testStep;
            currentStepOrHookMap = new HashMap<>();
            addHookStepToTestCaseMap(currentStepOrHookMap, hookTestStep.getHookType());
        } else {
            throw new IllegalStateException();
        }

    }

    private void handleTestStepFinished(TestStepFinished event) {
        currentStepOrHookMap.put(MATCH.value, createMatchMap(event.testStep, event.result));
        currentStepOrHookMap.put(RESULT.value, createResultMap(event.result));
    }

    private void handleEmbedding(EmbedEvent event) {
        addEmbeddingToHookMap(event.data, event.mimeType);
    }

    private void handleWrite(WriteEvent event) {
        addOutputToHookMap(event.text);
    }

    private void finishReport() {
        flushCurrentFeature();
    }

    /*
     * Feature processing methods
     * */
    public Map<String, Object> createFeatureMap(Feature feature, String uri) {
        Map<String, Object> featureMap = new HashMap<>();
        featureMap.put(URI.value, uri);
        featureMap.put(ELEMENTS.value, new ArrayList<Map<String, Object>>());
        if (feature != null) {
            featureMap.put(KEYWORD.value, feature.getKeyword());
            featureMap.put(NAME.value, feature.getName());
            featureMap.put(DESCRIPTION.value, feature.getDescription() != null ? feature.getDescription() : "");
            featureMap.put(LINE.value, feature.getLocation().getLine());
            featureMap.put(ID.value, convertToId(feature.getName()));
            featureMap.put(TAGS.value, feature.getTags());

        }
        return featureMap;
    }

    public Feature getFeature(String source) {
        Parser<GherkinDocument> parser = new Parser<>(new AstBuilder());
        TokenMatcher matcher = new TokenMatcher();
        GherkinDocument gherkinDocument;
        try {
            gherkinDocument = parser.parse(source, matcher);
        } catch (ParserException e) {
            return null;
        }
        return gherkinDocument.getFeature();
    }

    private void flushCurrentFeature() {
        File outFile = new File(outFolder, "Feature" + scenarioCount + "_cucumber.json");
        scenarioCount++;

        createDirectory(outFile);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
            writer.write(gson.toJson(Collections.singletonList(currentFeatureMap)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Background processing methods
     * */

    private void createDirectory(File file) {
        try {
            if (!outFolder.exists())
                file.getParentFile().mkdirs();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<String, Object> createBackground(Background background) {
        Map<String, Object> map = new HashMap<>();
        map.put(NAME.value, background.getName());
        map.put(LINE.value, background.getLocation().getLine());
        map.put(TYPE.value, BACKGROUND.value);
        map.put(KEYWORD.value, background.getKeyword());
        map.put(DESCRIPTION.value, background.getDescription() != null ? background.getDescription() : "");
        map.put(STEPS.value, new ArrayList<Map<String, Object>>());
        return map;
    }

    private boolean isBackgroundStep(PickleStepTestStep testStep) {
        return this.background.getSteps()
                .stream().anyMatch(s -> s.getText().equals(testStep.getStepText()) &&
                        s.getLocation().getLine() == testStep.getStepLine());
    }

    private boolean isFirstStepAfterBackground(PickleStepTestStep testStep) {
        return currentElementMap != currentTestCaseMap && !isBackgroundStep(testStep);
    }

    /*
     * TestCase/Scenario processing methods
     * */

    private Map<String, Object> createTestCase(TestCase testCase) {
        Map<String, Object> testCaseMap = new HashMap<>();
        testCaseMap.put(NAME.value, testCase.getName());
        testCaseMap.put(LINE.value, testCase.getLine());
        testCaseMap.put(TYPE.value, SCENARIO.value);

        testCaseMap.put(ID.value, calculateId(testCase));
        currentScenario = getScenario(testCase);
        testCaseMap.put(KEYWORD.value, currentScenario.getKeyword());
        testCaseMap.put(DESCRIPTION.value, currentScenario.getDescription() != null ? currentScenario.getDescription() : "");

        testCaseMap.put(STEPS.value, new ArrayList<Map<String, Object>>());
        if (!testCase.getTags().isEmpty()) {
            List<Map<String, Object>> tagList = new ArrayList<>();
            for (PickleTag tag : testCase.getTags()) {
                Map<String, Object> tagMap = new HashMap<>();
                tagMap.put(NAME.value, tag.getName());
                tagList.add(tagMap);
            }
            testCaseMap.put(TAGS.value, tagList);
        }
        return testCaseMap;
    }

    private <T extends ScenarioDefinition> T getScenario(TestCase testCase) {
        List<ScenarioDefinition> featureScenarios = currentFeature.getChildren();
        for (ScenarioDefinition scenario : featureScenarios) {
            if (scenario instanceof Background) {
                continue;
            }
            if (testCase.getLine() == scenario.getLocation().getLine() && testCase.getName().equals(scenario.getName())) {
                return (T) scenario;
            } else {
                if (scenario instanceof ScenarioOutline) {
                    for (Examples example : ((ScenarioOutline) scenario).getExamples()) {
                        for (TableRow tableRow : example.getTableBody()) {
                            if (tableRow.getLocation().getLine() == testCase.getLine()) {
                                return (T) scenario;
                            }
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("Scenario can't be null!");
    }

    private String calculateId(TestCase testCase) {
        return currentFeatureMap.get(ID.value) + ";" + convertToId(testCase.getName()) + testCase.getLine();
    }

    /*
     * Step processing methods
     * */

    private Map<String, Object> createTestStep(PickleStepTestStep testStep) {
        Map<String, Object> stepMap = new HashMap<>();
        stepMap.put(NAME.value, testStep.getStepText());
        stepMap.put(LINE.value, testStep.getStepLine());

        Optional<Step> first = currentScenario.getSteps().stream()
                .filter(s -> s.getLocation().getLine() == testStep.getStepLine()).findFirst();

        if (!first.isPresent() && background != null) {
            first = background.getSteps().stream()
                    .filter(s -> s.getLocation().getLine() == testStep.getStepLine()).findFirst();
        }

        if (!first.isPresent()) {
            throw new RuntimeException("Undefined step[" + testStep.getStepText() + "]");
        }

        if (!testStep.getStepArgument().isEmpty()) {
            Argument argument = testStep.getStepArgument().get(0);
            if (argument instanceof PickleString) {
                stepMap.put(DOC_STRING.value, createDocStringMap(argument, first.get()));
            } else if (argument instanceof PickleTable) {
                stepMap.put(ROWS.value, createDataTableList(argument));
            }
        }

        stepMap.put(KEYWORD.value, first.get().getKeyword());

        return stepMap;
    }

    private Map<String, Object> createMatchMap(TestStep step, Result result) {
        Map<String, Object> matchMap = new HashMap<>();
        if (step instanceof PickleStepTestStep) {
            PickleStepTestStep testStep = (PickleStepTestStep) step;
            if (!testStep.getDefinitionArgument().isEmpty()) {
                List<Map<String, Object>> argumentList = new ArrayList<>();
                for (cucumber.api.Argument argument : testStep.getDefinitionArgument()) {
                    Map<String, Object> argumentMap = new HashMap<>();
                    if (argument.getValue() != null) {
                        argumentMap.put(VAL.value, argument.getValue());
                        argumentMap.put(OFFSET.value, argument.getStart());
                    }
                    argumentList.add(argumentMap);
                }
                matchMap.put(ARGUMENTS.value, argumentList);
            }
        }
        if (!result.is(Result.Type.UNDEFINED)) {
            matchMap.put(LOCATION.value, step.getCodeLocation());
        }
        return matchMap;
    }

    private Map<String, Object> createDocStringMap(Argument argument, Step step) {
        Map<String, Object> docStringMap = new HashMap<>();
        PickleString docString = ((PickleString) argument);
        docStringMap.put(VALUE.value, docString.getContent());
        docStringMap.put(LINE.value, docString.getLocation().getLine());

        docStringMap.put(CONTENT_TYPE.value, ((DocString) step.getArgument()).getContentType());

        return docStringMap;
    }

    private Map<String, Object> createResultMap(Result result) {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(STATUS.value, result.getStatus().lowerCaseName());
        if (result.getErrorMessage() != null) {
            resultMap.put(ERROR_MESSAGE.value, result.getErrorMessage());
        }
        if (result.getDuration() != null && result.getDuration() != 0) {
            resultMap.put(DURATION.value, result.getDuration());
        }
        return resultMap;
    }

    private void addHookStepToTestCaseMap(Map<String, Object> currentStepOrHookMap, HookType hookType) {
        String hookName;
        if (hookType.toString().contains(AFTER.value))
            hookName = AFTER.value;
        else
            hookName = BEFORE.value;


        Map<String, Object> map;
        switch (hookType) {
            case Before:
                map = currentTestCaseMap;
                break;
            case After:
                map = currentTestCaseMap;
                break;
            case BeforeStep:
                map = currentBeforeStepHookList;
                break;
            case AfterStep:
                map = currentStepsList.get(currentStepsList.size() - 1);
                break;
            default:
                map = currentTestCaseMap;
        }

        if (!map.containsKey(hookName)) {
            map.put(hookName, new ArrayList<Map<String, Object>>());
        }
        ((List<Map<String, Object>>) map.get(hookName)).add(currentStepOrHookMap);
    }

    private List<Map<String, Object>> createDataTableList(Argument argument) {
        List<Map<String, Object>> rowList = new ArrayList<>();
        for (PickleRow row : ((PickleTable) argument).getRows()) {
            Map<String, Object> rowMap = new HashMap<>();
            rowMap.put(CELLS.value, createCellList(row));
            rowList.add(rowMap);
        }
        return rowList;
    }

    private List<String> createCellList(PickleRow row) {
        List<String> cells = new ArrayList<>();
        for (PickleCell cell : row.getCells()) {
            cells.add(cell.getValue());
        }
        return cells;
    }

    /*
     * Embedding/Write processing methods
     * */
    private Map<String, Object> createEmbeddingMap(byte[] data, String mimeType) {
        Map<String, Object> embedMap = new HashMap<>();
        embedMap.put(MIME_TYPE.value, mimeType);
        embedMap.put(DATA.value, Base64.encodeBytes(data));
        return embedMap;
    }

    private void addEmbeddingToHookMap(byte[] data, String mimeType) {
        if (!currentStepOrHookMap.containsKey(EMBEDDINGS.value)) {
            currentStepOrHookMap.put(EMBEDDINGS.value, new ArrayList<Map<String, Object>>());
        }
        Map<String, Object> embedMap = createEmbeddingMap(data, mimeType);
        ((List<Map<String, Object>>) currentStepOrHookMap.get(EMBEDDINGS.value)).add(embedMap);
    }

    private void addOutputToHookMap(String text) {
        if (!currentStepOrHookMap.containsKey(OUTPUT.value)) {
            currentStepOrHookMap.put(OUTPUT.value, new ArrayList<String>());
        }
        ((List<String>) currentStepOrHookMap.get(OUTPUT.value)).add(text);
    }


    public String convertToId(String name) {
        return name.replaceAll("[\\s'_,!]", "-").toLowerCase();
    }

}
