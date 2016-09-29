package com.ethercamp.qa.ssh;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;

/**
 * Created by Anton Nashatyrev on 23.05.2016.
 */
public class ExecSshConnection extends AbstractSshConnection {

    public ExecSshConnection(Session session) {
        super(session);
    }

    @Override
    public void init() {

    }

    ChannelExec exec;

    @Override
    protected void submitCommand(String command) throws Exception {
        exec = (ChannelExec) session.openChannel("exec");
        exec.setCommand(command);
        exec.start();
    }

    @Override
    protected ExecResult getResult(boolean async, boolean needExitCode) throws Exception {
        return new ExecResult() {
            @Override
            public boolean isComplete() {
                return exec.isClosed();
            }

            @Override
            public synchronized int getExitCode() throws InterruptedException {
                return exec.getExitStatus();
            }
        };
    }
}
