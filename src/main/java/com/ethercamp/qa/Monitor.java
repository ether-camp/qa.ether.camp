package com.ethercamp.qa;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by Anton Nashatyrev on 20.05.2016.
 */
public class Monitor implements TestListener {

    private static final long FailNewBlockTimeout = 5 * 60 * 1000;

    public Map<TestScenario, ScenarioState> states = new LinkedHashMap<>();

    public Monitor() {
        new Thread(this::checkLoop).start();
    }

    public void addNew(TestScenario ts) {
        states.put(ts, new ScenarioState());
        for (ScenarioState state : states.values()) {
            state.setAllStates(states.values());
        }
        updated();
    }

    private void checkLoop() {
        while (true) {

            for (Map.Entry<TestScenario, ScenarioState> entry : states.entrySet()) {
                if (entry.getValue().getLastBlockTime() > 0 &&
                        System.currentTimeMillis() - entry.getValue().getLastBlockTime() > FailNewBlockTimeout) {
                    entry.getValue().setState(ScenarioState.State.CompleteFail);
                    updated();
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void scenarioSetUp(TestScenario ts) {
        states.computeIfAbsent(ts, s -> new ScenarioState()).setState(ScenarioState.State.Ready);
        updated();
    }

    @Override
    public void scenarioStarted(TestScenario ts) {
        states.computeIfAbsent(ts, s -> new ScenarioState()).setState(ScenarioState.State.Running);
        updated();
    }

    @Override
    public void scenarioStopped(TestScenario ts) {
        states.computeIfAbsent(ts, s -> new ScenarioState()).setState(ScenarioState.State.Ready);
        updated();
    }

    @Override
    public void newBlockImported(TestScenario ts, int block, boolean best) {
        ScenarioState scenarioState = states.computeIfAbsent(ts, s -> new ScenarioState());
        scenarioState.setState(ScenarioState.State.Running);
        scenarioState.newBlock(block);
        updated();
    }

    @Override
    public void logErrorFound(TestScenario ts, String error) {
        ScenarioState scenarioState = states.computeIfAbsent(ts, s -> new ScenarioState());
        scenarioState.addError(error);
        updated();
    }

    @Override
    public void scenarioCompleted(TestScenario ts, boolean passed) {
        ScenarioState scenarioState = states.computeIfAbsent(ts, s -> new ScenarioState());
        scenarioState.setState(passed ? ScenarioState.State.CompleteOK : ScenarioState.State.CompleteFail);
        updated();
    }

    @Override
    public void scenarioFailed(TestScenario ts, Exception e) {
        ScenarioState scenarioState = states.computeIfAbsent(ts, s -> new ScenarioState());
        scenarioState.setState(ScenarioState.State.Error);
        scenarioState.setError(e);
        e.printStackTrace();
        updated();
    }

    protected void updated() {}
}
