package com.ethercamp.qa;

import com.ethercamp.qa.ssh.SshServer;

/**
 * Created by Anton Nashatyrev on 18.05.2016.
 */
public class TestEnv {

    SshServer server;
    String workDir;

    String dbDir;

    Integer port;

    public TestEnv(SshServer server, String workDir) {
        this.server = server;
        this.workDir = workDir;
    }

    public TestEnv setDbDir(String dbDir) {
        this.dbDir = dbDir;
        return this;
    }

    public TestEnv setPort(Integer port) {
        this.port = port;
        return this;
    }

    public SshServer getServer() {
        return server;
    }

    public String getWorkDir() {
        return workDir;
    }

    public String getDbDir() {
        return dbDir;
    }

    public Integer getPort() {
        return port;
    }
}
