package com.ethercamp.qa.scenarios;

import com.ethercamp.qa.TestEnv;
import com.ethercamp.qa.TestListener;

/**
 * Created by Anton Nashatyrev on 19.05.2016.
 */
public class ProjectTestScenario extends CommonTestScenario {
    String repository;
    String gradleTarget;

    public ProjectTestScenario(String repository) {
        this.repository = repository;
    }

    public ProjectTestScenario(TestEnv env, String repository, TestListener listener) {
        super(env, listener);
        this.repository = repository;
    }

    public ProjectTestScenario setGradleTarget(String gradleTarget) {
        this.gradleTarget = gradleTarget;
        return this;
    }

    @Override
    public String getRepository() {
        return repository;
    }

    @Override
    public String getRunningDir() {
        return getRepoDir();
    }

    @Override
    protected String getGradleTarget() {
        return gradleTarget;
    }
}
