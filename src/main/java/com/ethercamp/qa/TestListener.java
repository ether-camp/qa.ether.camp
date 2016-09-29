package com.ethercamp.qa;

/**
 * Created by Anton Nashatyrev on 18.05.2016.
 */
public interface TestListener {

    class Adapter implements TestListener {
        public void scenarioSetUp(TestScenario ts) {}
        public void scenarioStarted(TestScenario ts) {}
        public void scenarioStopped(TestScenario ts) {}
        public void newBlockImported(TestScenario ts, int block, boolean best) {}
        public void logErrorFound(TestScenario ts, String error) {}
        public void scenarioCompleted(TestScenario ts, boolean passed) {}
        public void scenarioFailed(TestScenario ts, Exception e) {}
    }

    void  scenarioSetUp(TestScenario ts);

    void scenarioStarted(TestScenario ts);

    void scenarioStopped(TestScenario ts);

    void newBlockImported(TestScenario ts, int block, boolean best);

    void logErrorFound(TestScenario ts, String error);

    void scenarioCompleted(TestScenario ts, boolean passed);

    void scenarioFailed(TestScenario ts, Exception e);
}
