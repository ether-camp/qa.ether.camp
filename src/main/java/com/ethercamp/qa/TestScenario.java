package com.ethercamp.qa;

import com.ethercamp.qa.ssh.SshConnection;
import org.apache.commons.io.output.TeeOutputStream;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * Created by Anton Nashatyrev on 18.05.2016.
 */
public abstract class TestScenario {
    protected TestEnv env;
    private SshConnection con;
    protected TestListener listener = new TestListener.Adapter();
    private String shortDescr;
    private String longDescr;

    public TestScenario() {
    }

    public TestScenario(TestEnv env, TestListener listener) {
        this.env = env;
        this.listener = listener;
    }

    public TestScenario setEnv(TestEnv env) {
        this.env = env;
        return this;
    }

    public void setListener(TestListener listener) {
        this.listener = listener;
    }

    public SshConnection getConnection() {
        try {
            if (con == null || con.isClosed()) {
                con = env.server.createConnection();
                con.init(new TeeOutputStream(new FileOutputStream(getId() + ".log", true), System.out));
            }
            return con;
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public String getId() {
        return getEnv().getServer().host + "-" + getEnv().getPort();
    }

    public void setup() {
        try {
            setupImpl();
            listener.scenarioSetUp(this);
        } catch (Exception e) {
            listener.scenarioFailed(this, e);
        }
    }

    protected abstract void setupImpl() throws Exception;

    public void start() {
        try {
            startImpl();
        } catch (Exception e) {
            listener.scenarioFailed(this, e);
        }
    }
    public abstract void startImpl() throws Exception;

    public void monitor() {
        new Thread() {
            @Override
            public void run() {
                try {
                    monitorImpl();
                } catch (Exception e) {
                    listener.scenarioFailed(TestScenario.this, e);
                }
            }
        }.start();
    }

    public abstract void monitorImpl() throws Exception;

    public void stop() {}

    public void cleanup() {}

    public TestEnv getEnv() {
        return env;
    }

    public TestScenario setDescription(String shortDescr, String longDescr) {
        this.shortDescr = shortDescr;
        this.longDescr = longDescr;
        return this;
    }

    public String getShortDescr() {
        return shortDescr;
    }

    public String getLongDescr() {
        return longDescr;
    }
}
