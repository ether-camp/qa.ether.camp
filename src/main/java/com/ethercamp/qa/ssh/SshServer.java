package com.ethercamp.qa.ssh;

/**
 * Created by Anton Nashatyrev on 18.05.2016.
 */
public class SshServer {
    SshConnector connector;

    public String host;
    public int port = 22;
    public String user;
    public String pass;

    public SshServer(String host, String user) {
        this(null, host, user);
    }

    public SshServer(SshConnector connector, String host, String user) {
        this.connector = connector;
        this.host = host;
        this.user = user;
    }

    public SshServer withPort(int port) {
        this.port = port;
        return this;
    }

    public SshServer withPassword(String pass) {
        this.pass = pass;
        return this;
    }

    public void setConnector(SshConnector connector) {
        this.connector = connector;
    }

    public SshConnection createConnection() {
        return connector.connect(host, port, user, pass);
    }
}
